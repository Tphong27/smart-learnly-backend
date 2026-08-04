from __future__ import annotations

import json
import os
import random
import re
import sys
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo

SCOPES = ["https://www.googleapis.com/auth/spreadsheets"]
DEFAULT_SPREADSHEET_ID = "1VX5JxJClV1dsZIEElCP5l73Dw-_YIleTs3KH_iLYo4o"
TEMPLATE_SHEET_TITLE = "extractVideoId"
MAX_WRITE_RETRIES = 7
TEST_CASE_HEADER_ROW = 7
TEST_CASE_START_COLUMN = 5
TEMPLATE_END_COLUMN = 19
MISSING = object()

ResultKey = tuple[str, str]
ExecutionResults = dict[ResultKey, "ExecutedCase"]


@dataclass(frozen=True)
class ExecutedCase:
    status: str
    actual: str
    duration_seconds: float


@dataclass(frozen=True)
class DetailSheet:
    matrix: list[list[Any]]
    condition_start_row: int
    condition_end_row: int
    confirm_start_row: int
    confirm_end_row: int
    result_start_row: int
    result_end_row: int
    case_start_column: int
    case_end_column: int
    label_rows: tuple[int, ...]


def repository_root() -> Path:
    return Path(__file__).resolve().parents[2]


def load_manifest() -> dict[str, Any]:
    path = Path(__file__).with_name("khiem-complex-method-cases.json")
    manifest = json.loads(path.read_text(encoding="utf-8"))
    validate_manifest(manifest)
    return manifest


def validate_manifest(manifest: dict[str, Any]) -> None:
    methods = manifest.get("methods")
    if not isinstance(methods, list) or not methods:
        raise ValueError("Manifest must contain a non-empty 'methods' list.")

    required_method_fields = (
        "moduleName",
        "methodName",
        "functionCode",
        "sheetName",
        "testClass",
        "description",
        "cases",
    )
    sheet_names: set[str] = set()
    function_codes: set[str] = set()
    test_names_by_class: set[ResultKey] = set()

    for method_index, method in enumerate(methods, start=1):
        location = f"methods[{method_index - 1}]"
        for field in required_method_fields:
            if field not in method:
                raise ValueError(f"{location} is missing '{field}'.")

        sheet_name = str(method["sheetName"]).strip()
        if not sheet_name:
            raise ValueError(f"{location}.sheetName must not be blank.")
        if len(sheet_name) > 100 or re.search(r"[:\\/?*\[\]]", sheet_name):
            raise ValueError(
                f"{location}.sheetName is not a valid Google Sheets title: "
                f"{sheet_name!r}"
            )
        if sheet_name in sheet_names:
            raise ValueError(
                f"Each method needs a different sheetName; duplicate: "
                f"{sheet_name!r}"
            )
        sheet_names.add(sheet_name)

        function_code = str(method["functionCode"]).strip()
        if function_code in function_codes:
            raise ValueError(
                f"Each method needs a different functionCode; duplicate: "
                f"{function_code!r}"
            )
        function_codes.add(function_code)

        cases = method["cases"]
        if not isinstance(cases, list) or not cases:
            raise ValueError(f"{location}.cases must be a non-empty list.")

        canonical_ids: list[str] = []
        for case_index, case in enumerate(cases, start=1):
            case_location = f"{location}.cases[{case_index - 1}]"
            for field in ("javaTestName", "type"):
                if field not in case:
                    raise ValueError(
                        f"{case_location} is missing '{field}'."
                    )

            case_type = str(case["type"]).upper()
            if case_type not in {"N", "A", "B"}:
                raise ValueError(
                    f"{case_location}.type must be N, A, or B."
                )

            result_key = (
                str(method["testClass"]),
                normalize_test_name(str(case["javaTestName"])),
            )
            if result_key in test_names_by_class:
                raise ValueError(
                    "javaTestName must be unique inside a testClass: "
                    f"{result_key[0]}.{result_key[1]}"
                )
            test_names_by_class.add(result_key)

            preconditions = case.get("preconditions", MISSING)
            if preconditions is not MISSING and not isinstance(
                preconditions,
                dict,
            ):
                raise ValueError(
                    f"{case_location}.preconditions must be an object."
                )

            confirm = case.get("confirm", MISSING)
            if confirm is not MISSING and not isinstance(confirm, dict):
                raise ValueError(
                    f"{case_location}.confirm must be an object."
                )

            declared_id = str(case.get("id", "")).strip().upper()
            if re.fullmatch(r"UTCID\d+", declared_id):
                canonical_ids.append(declared_id)

        if canonical_ids and len(canonical_ids) != len(cases):
            raise ValueError(
                f"{location}: either every case or no case must use the "
                "short UTCID01-style id."
            )
        if canonical_ids:
            expected_ids = [
                f"UTCID{index:02d}"
                for index in range(1, len(cases) + 1)
            ]
            if canonical_ids != expected_ids:
                raise ValueError(
                    f"{location}: case ids must restart at UTCID01 and be "
                    f"sequential; expected {expected_ids}."
                )


def surefire_report_path(test_class: str) -> Path:
    return (
        repository_root()
        / "target"
        / "surefire-reports"
        / f"TEST-{test_class}.xml"
    )


def normalize_test_name(value: str) -> str:
    return value.removesuffix("()").strip()


def first_failure_line(node: ET.Element) -> str:
    failure = node.find("failure")
    if failure is None:
        failure = node.find("error")
    if failure is None:
        return ""

    message = failure.attrib.get("message") or failure.text or "Test failed"
    return " ".join(message.strip().split())[:240]


def read_surefire_results(
    manifest: dict[str, Any],
) -> ExecutionResults:
    results: ExecutionResults = {}
    test_classes = {
        method["testClass"]
        for method in manifest["methods"]
    }

    for test_class in test_classes:
        report_path = surefire_report_path(test_class)
        if not report_path.exists():
            raise FileNotFoundError(
                f"Fresh Surefire report was not generated: {report_path}"
            )

        root = ET.parse(report_path).getroot()
        nodes = root.findall(".//testcase")
        if root.tag == "testsuite":
            nodes = root.findall("testcase")

        for node in nodes:
            test_name = normalize_test_name(node.attrib.get("name", ""))
            duration = float(node.attrib.get("time", "0") or 0)

            if node.find("skipped") is not None:
                executed = ExecutedCase("U", "Untested / skipped", duration)
            else:
                failure = first_failure_line(node)
                executed = (
                    ExecutedCase("F", failure, duration)
                    if failure
                    else ExecutedCase("P", "Passed as expected", duration)
                )

            results[(test_class, test_name)] = executed

    expected_names = {
        (
            method["testClass"],
            normalize_test_name(case["javaTestName"]),
        )
        for method in manifest["methods"]
        for case in method["cases"]
    }
    missing = expected_names.difference(results)
    for result_key in missing:
        results[result_key] = ExecutedCase(
            "U",
            "Test case was not present in the fresh Surefire XML report",
            0,
        )

    return results


def result_key(
    method: dict[str, Any],
    case: dict[str, Any],
) -> ResultKey:
    return (
        method["testClass"],
        normalize_test_name(case["javaTestName"]),
    )


def case_result(
    method: dict[str, Any],
    case: dict[str, Any],
    results: ExecutionResults,
) -> ExecutedCase:
    return results[result_key(method, case)]


def expected_results(
    manifest: dict[str, Any],
    results: ExecutionResults,
) -> list[ExecutedCase]:
    return [
        case_result(method, case, results)
        for method in manifest["methods"]
        for case in method["cases"]
    ]


def sheets_service():
    from google.oauth2 import service_account
    from googleapiclient.discovery import build

    credentials_path = os.getenv("GOOGLE_APPLICATION_CREDENTIALS")
    if not credentials_path:
        raise RuntimeError(
            "GOOGLE_APPLICATION_CREDENTIALS is not set."
        )

    credentials_file = Path(credentials_path)
    if not credentials_file.is_file():
        raise FileNotFoundError(
            f"Service-account JSON does not exist: {credentials_file}"
        )

    credentials = service_account.Credentials.from_service_account_file(
        credentials_file,
        scopes=SCOPES,
    )
    return build(
        "sheets",
        "v4",
        credentials=credentials,
        cache_discovery=False,
    )


def execute_write_with_retry(request, operation: str):
    from googleapiclient.errors import HttpError

    for attempt in range(MAX_WRITE_RETRIES + 1):
        try:
            return request.execute()
        except HttpError as error:
            status = getattr(error.resp, "status", None)
            if status != 429 or attempt >= MAX_WRITE_RETRIES:
                raise

            retry_after_header = error.resp.get("retry-after")
            retry_after = (
                float(retry_after_header)
                if retry_after_header
                else 0.0
            )
            exponential_delay = min(60.0, 2.0 ** (attempt + 1))
            delay = max(
                retry_after,
                exponential_delay + random.uniform(0.0, 1.0),
            )
            print(
                f"Google Sheets quota reached while {operation}; "
                f"retrying in {delay:.1f} seconds "
                f"({attempt + 1}/{MAX_WRITE_RETRIES}).",
                file=sys.stderr,
            )
            time.sleep(delay)


def spreadsheet_metadata(service, spreadsheet_id: str) -> dict[str, Any]:
    return (
        service.spreadsheets()
        .get(
            spreadsheetId=spreadsheet_id,
            fields="spreadsheetId,properties.title,sheets.properties",
        )
        .execute()
    )


def sheet_map(metadata: dict[str, Any]) -> dict[str, int]:
    return {
        sheet["properties"]["title"]: sheet["properties"]["sheetId"]
        for sheet in metadata.get("sheets", [])
    }


def quote_sheet(title: str) -> str:
    return "'" + title.replace("'", "''") + "'"


def a1_column(column: int) -> str:
    if column < 1:
        raise ValueError("A1 column number must be positive.")

    letters: list[str] = []
    current = column
    while current:
        current, remainder = divmod(current - 1, 26)
        letters.append(chr(ord("A") + remainder))
    return "".join(reversed(letters))


def read_values(
    service,
    spreadsheet_id: str,
    sheet_title: str,
    cell_range: str,
) -> list[list[Any]]:
    response = (
        service.spreadsheets()
        .values()
        .get(
            spreadsheetId=spreadsheet_id,
            range=f"{quote_sheet(sheet_title)}!{cell_range}",
            valueRenderOption="FORMATTED_VALUE",
        )
        .execute()
    )
    return response.get("values", [])


def get_cell(rows: list[list[Any]], row: int, column: int) -> str:
    if row >= len(rows) or column >= len(rows[row]):
        return ""
    return str(rows[row][column]).strip()


def case_counts(
    method: dict[str, Any],
    results: ExecutionResults,
) -> dict[str, int]:
    counts = {
        "P": 0,
        "F": 0,
        "U": 0,
        "N": 0,
        "A": 0,
        "B": 0,
    }
    for case in method["cases"]:
        counts[case_result(method, case, results).status] += 1
        counts[str(case["type"]).upper()] += 1
    return counts


def report_value(value: Any) -> str:
    if value is None:
        return "null"
    if value is True:
        return "TRUE"
    if value is False:
        return "FALSE"
    if isinstance(value, (dict, list)):
        return json.dumps(value, ensure_ascii=False, separators=(",", ":"))
    if isinstance(value, str) and value == "":
        return '""'
    return str(value)


def group_indexed_values(
    indexed_values: list[tuple[int, Any]],
) -> list[tuple[str, list[int]]]:
    grouped: dict[str, list[int]] = {}
    for case_index, value in indexed_values:
        grouped.setdefault(report_value(value), []).append(case_index)
    return list(grouped.items())


def case_preconditions(case: dict[str, Any]) -> dict[str, Any]:
    detailed = case.get("preconditions", MISSING)
    if detailed is not MISSING:
        return detailed

    if "input" in case:
        return {"Input / mocked state": case["input"]}
    return {}


def precondition_groups(
    method: dict[str, Any],
) -> list[tuple[str, list[tuple[str, list[int]]]]]:
    field_names: list[str] = []
    values_by_field: dict[str, list[tuple[int, Any]]] = {}

    for case_index, case in enumerate(method["cases"]):
        for field_name, value in case_preconditions(case).items():
            normalized_name = str(field_name).strip()
            if not normalized_name:
                raise ValueError(
                    f"{method['functionCode']} contains a blank "
                    "precondition name."
                )
            if normalized_name not in values_by_field:
                field_names.append(normalized_name)
                values_by_field[normalized_name] = []
            values_by_field[normalized_name].append((case_index, value))

    return [
        (field_name, group_indexed_values(values_by_field[field_name]))
        for field_name in field_names
    ]


def case_confirm(case: dict[str, Any]) -> dict[str, Any]:
    detailed = case.get("confirm", MISSING)
    if detailed is not MISSING:
        return detailed

    legacy: dict[str, Any] = {}
    for key in ("return", "exception", "logMessage"):
        if key in case:
            legacy[key] = case[key]
    if not legacy and "expected" in case:
        legacy["return"] = case["expected"]
    return legacy


def confirm_groups(
    method: dict[str, Any],
) -> list[tuple[str, list[tuple[str, list[int]]]]]:
    sections = (
        ("return", "Return"),
        ("exception", "Exception"),
        ("logMessage", "Log message"),
    )
    result: list[tuple[str, list[tuple[str, list[int]]]]] = []

    for key, label in sections:
        indexed_values: list[tuple[int, Any]] = []
        for case_index, case in enumerate(method["cases"]):
            values = case_confirm(case)
            if key in values:
                indexed_values.append((case_index, values[key]))
        result.append((label, group_indexed_values(indexed_values)))

    return result


def detail_sheet(
    manifest: dict[str, Any],
    method: dict[str, Any],
    results: ExecutionResults,
) -> DetailSheet:
    case_count = len(method["cases"])
    case_start_column = TEST_CASE_START_COLUMN
    case_end_column = case_start_column + case_count - 1
    column_count = max(TEMPLATE_END_COLUMN, case_end_column)
    conditions = precondition_groups(method)
    confirmations = confirm_groups(method)

    condition_start_row = 8
    current_row = condition_start_row + 3
    condition_rows: list[tuple[int, str, list[tuple[int, str, list[int]]]]] = []
    for field_name, groups in conditions:
        label_row = current_row
        current_row += 1
        value_rows: list[tuple[int, str, list[int]]] = []
        if groups:
            for value, case_indexes in groups:
                value_rows.append((current_row, value, case_indexes))
                current_row += 1
        else:
            current_row += 1
        condition_rows.append((label_row, field_name, value_rows))

    if not condition_rows:
        current_row += 1
    condition_end_row = current_row - 1

    confirm_start_row = condition_end_row + 1
    current_row = confirm_start_row
    confirm_rows: list[tuple[int, str, list[tuple[int, str, list[int]]]]] = []
    for label, groups in confirmations:
        label_row = current_row
        current_row += 1
        value_rows: list[tuple[int, str, list[int]]] = []
        if groups:
            for value, case_indexes in groups:
                value_rows.append((current_row, value, case_indexes))
                current_row += 1
        else:
            current_row += 1
        confirm_rows.append((label_row, label, value_rows))

    confirm_end_row = current_row - 1
    result_start_row = confirm_end_row + 1
    result_end_row = result_start_row + 3
    matrix: list[list[Any]] = [
        [""] * column_count for _ in range(result_end_row)
    ]

    def put(row: int, column: int, value: Any) -> None:
        matrix[row - 1][column - 1] = value

    def put_value_rows(
        value_rows: list[tuple[int, str, list[int]]],
    ) -> None:
        for row, value, case_indexes in value_rows:
            put(row, 4, value)
            for case_index in case_indexes:
                put(row, case_start_column + case_index, "O")

    counts = case_counts(method, results)
    executed_date = datetime.now(
        ZoneInfo("Asia/Ho_Chi_Minh")
    ).strftime("%d/%m/%Y")

    put(1, 1, "Code Module")
    put(1, 3, method["moduleName"])
    put(1, 5, "Method")
    put(1, 11, method["methodName"])
    put(2, 1, "Created By")
    put(2, 3, manifest["createdBy"])
    put(2, 5, "Executed By")
    put(2, 11, manifest["executedBy"])
    put(3, 1, "Test requirement")
    put(3, 3, method["description"])
    put(4, 1, "Passed")
    put(4, 3, "Failed")
    put(4, 5, "Untested")
    put(4, 11, "N/A/B")
    put(4, 14, "Total Test Cases")
    put(5, 1, counts["P"])
    put(5, 3, counts["F"])
    put(5, 5, counts["U"])
    put(5, 11, counts["N"])
    put(5, 12, counts["A"])
    put(5, 13, counts["B"])
    put(5, 14, case_count)

    put(condition_start_row, 1, "Condition")
    put(condition_start_row, 2, "Precondition")
    for label_row, field_name, value_rows in condition_rows:
        put(label_row, 2, field_name)
        put_value_rows(value_rows)

    put(confirm_start_row, 1, "Confirm")
    for label_row, label, value_rows in confirm_rows:
        put(label_row, 2, label)
        put_value_rows(value_rows)

    put(result_start_row, 1, "Result")
    put(
        result_start_row,
        2,
        "Type (N: Normal, A: Abnormal, B: Boundary)",
    )
    put(result_start_row + 1, 2, "Passed/Failed")
    put(result_start_row + 2, 2, "Executed Date")
    put(result_start_row + 3, 2, "Defect ID")

    for case_index, case in enumerate(method["cases"]):
        column = case_start_column + case_index
        executed = case_result(method, case, results)
        put(
            TEST_CASE_HEADER_ROW,
            column,
            f"UTCID{case_index + 1:02d}",
        )
        put(result_start_row, column, str(case["type"]).upper())
        put(result_start_row + 1, column, executed.status)
        put(
            result_start_row + 2,
            column,
            executed_date if executed.status in {"P", "F"} else "",
        )
        put(result_start_row + 3, column, case.get("defectId", ""))

    label_rows = tuple(
        [condition_start_row]
        + [row for row, _, _ in condition_rows]
        + [row for row, _, _ in confirm_rows]
        + list(range(result_start_row, result_end_row + 1))
    )

    return DetailSheet(
        matrix=matrix,
        condition_start_row=condition_start_row,
        condition_end_row=condition_end_row,
        confirm_start_row=confirm_start_row,
        confirm_end_row=confirm_end_row,
        result_start_row=result_start_row,
        result_end_row=result_end_row,
        case_start_column=case_start_column,
        case_end_column=case_end_column,
        label_rows=label_rows,
    )


def grid_range(
    sheet_id: int,
    start_row: int,
    end_row: int,
    start_column: int,
    end_column: int,
) -> dict[str, int]:
    return {
        "sheetId": sheet_id,
        "startRowIndex": start_row - 1,
        "endRowIndex": end_row,
        "startColumnIndex": start_column - 1,
        "endColumnIndex": end_column,
    }


def method_sheet_format_requests(
    sheet_id: int,
    detail: DetailSheet,
) -> list[dict[str, Any]]:
    blue = {"red": 0.0, "green": 0.0, "blue": 0.5}
    white = {"red": 1.0, "green": 1.0, "blue": 1.0}
    black = {"red": 0.0, "green": 0.0, "blue": 0.0}
    thin_black_border = {
        "style": "SOLID",
        "color": black,
    }
    table_end_column = max(19, detail.case_end_column)
    reset_end_column = max(26, table_end_column)
    reset_end_row = max(100, detail.result_end_row)

    requests: list[dict[str, Any]] = [
        {
            "unmergeCells": {
                "range": grid_range(
                    sheet_id,
                    7,
                    reset_end_row,
                    1,
                    reset_end_column,
                )
            }
        },
        {
            "repeatCell": {
                "range": grid_range(
                    sheet_id,
                    7,
                    detail.result_end_row,
                    1,
                    table_end_column,
                ),
                "cell": {
                    "userEnteredFormat": {
                        "backgroundColor": white,
                        "textFormat": {
                            "foregroundColor": black,
                            "fontFamily": "Arial",
                            "fontSize": 10,
                        },
                        "verticalAlignment": "MIDDLE",
                        "wrapStrategy": "WRAP",
                        "borders": {
                            "top": thin_black_border,
                            "bottom": thin_black_border,
                            "left": thin_black_border,
                            "right": thin_black_border,
                        },
                    }
                },
                "fields": (
                    "userEnteredFormat(backgroundColor,textFormat,"
                    "verticalAlignment,wrapStrategy,borders)"
                ),
            }
        },
        {
            "repeatCell": {
                "range": grid_range(
                    sheet_id,
                    7,
                    7,
                    1,
                    table_end_column,
                ),
                "cell": {
                    "userEnteredFormat": {
                        "backgroundColor": blue,
                        "horizontalAlignment": "CENTER",
                        "textFormat": {
                            "foregroundColor": white,
                            "bold": True,
                            "fontFamily": "Arial",
                            "fontSize": 10,
                        },
                    }
                },
                "fields": (
                    "userEnteredFormat(backgroundColor,"
                    "horizontalAlignment,textFormat)"
                ),
            }
        },
        {
            "repeatCell": {
                "range": grid_range(
                    sheet_id,
                    8,
                    detail.result_end_row,
                    4,
                    4,
                ),
                "cell": {
                    "userEnteredFormat": {
                        "horizontalAlignment": "RIGHT",
                        "wrapStrategy": "WRAP",
                    }
                },
                "fields": (
                    "userEnteredFormat(horizontalAlignment,wrapStrategy)"
                ),
            }
        },
        {
            "repeatCell": {
                "range": grid_range(
                    sheet_id,
                    8,
                    detail.result_end_row,
                    detail.case_start_column,
                    detail.case_end_column,
                ),
                "cell": {
                    "userEnteredFormat": {
                        "horizontalAlignment": "CENTER",
                        "textFormat": {
                            "bold": True,
                            "fontFamily": "Arial",
                            "fontSize": 11,
                        },
                    }
                },
                "fields": (
                    "userEnteredFormat(horizontalAlignment,textFormat)"
                ),
            }
        },
        {
            "updateSheetProperties": {
                "properties": {
                    "sheetId": sheet_id,
                    "gridProperties": {
                        "frozenRowCount": 0,
                        "frozenColumnCount": 0,
                    },
                },
                "fields": (
                    "gridProperties.frozenRowCount,"
                    "gridProperties.frozenColumnCount"
                ),
            }
        },
        {
            "updateDimensionProperties": {
                "range": {
                    "sheetId": sheet_id,
                    "dimension": "COLUMNS",
                    "startIndex": 0,
                    "endIndex": 1,
                },
                "properties": {"pixelSize": 125},
                "fields": "pixelSize",
            }
        },
        {
            "updateDimensionProperties": {
                "range": {
                    "sheetId": sheet_id,
                    "dimension": "COLUMNS",
                    "startIndex": 1,
                    "endIndex": 2,
                },
                "properties": {"pixelSize": 180},
                "fields": "pixelSize",
            }
        },
        {
            "updateDimensionProperties": {
                "range": {
                    "sheetId": sheet_id,
                    "dimension": "COLUMNS",
                    "startIndex": 2,
                    "endIndex": 3,
                },
                "properties": {"pixelSize": 24},
                "fields": "pixelSize",
            }
        },
        {
            "updateDimensionProperties": {
                "range": {
                    "sheetId": sheet_id,
                    "dimension": "COLUMNS",
                    "startIndex": 3,
                    "endIndex": 4,
                },
                "properties": {"pixelSize": 360},
                "fields": "pixelSize",
            }
        },
        {
            "updateDimensionProperties": {
                "range": {
                    "sheetId": sheet_id,
                    "dimension": "COLUMNS",
                    "startIndex": detail.case_start_column - 1,
                    "endIndex": detail.case_end_column,
                },
                "properties": {"pixelSize": 115},
                "fields": "pixelSize",
            }
        },
        {
            "updateDimensionProperties": {
                "range": {
                    "sheetId": sheet_id,
                    "dimension": "ROWS",
                    "startIndex": 6,
                    "endIndex": 7,
                },
                "properties": {"pixelSize": 42},
                "fields": "pixelSize",
            }
        },
    ]

    for start_row, end_row in (
        (
            detail.condition_start_row,
            detail.condition_end_row,
        ),
        (
            detail.confirm_start_row,
            detail.confirm_end_row,
        ),
        (
            detail.result_start_row,
            detail.result_end_row,
        ),
    ):
        section_range = grid_range(
            sheet_id,
            start_row,
            end_row,
            1,
            1,
        )
        requests.extend([
            {
                "mergeCells": {
                    "range": section_range,
                    "mergeType": "MERGE_ALL",
                }
            },
            {
                "repeatCell": {
                    "range": section_range,
                    "cell": {
                        "userEnteredFormat": {
                            "backgroundColor": blue,
                            "horizontalAlignment": "LEFT",
                            "verticalAlignment": "TOP",
                            "textFormat": {
                                "foregroundColor": white,
                                "bold": True,
                                "fontFamily": "Arial",
                                "fontSize": 10,
                            },
                        }
                    },
                    "fields": (
                        "userEnteredFormat(backgroundColor,"
                        "horizontalAlignment,verticalAlignment,textFormat)"
                    ),
                }
            },
        ])

    for row in detail.label_rows:
        requests.append({
            "repeatCell": {
                "range": grid_range(
                    sheet_id,
                    row,
                    row,
                    2,
                    2,
                ),
                "cell": {
                    "userEnteredFormat": {
                        "textFormat": {
                            "bold": True,
                            "fontFamily": "Arial",
                            "fontSize": 10,
                        }
                    }
                },
                "fields": "userEnteredFormat.textFormat",
            }
        })

    return requests


def ensure_all_method_sheets(
    service,
    spreadsheet_id: str,
    manifest: dict[str, Any],
) -> dict[str, int]:
    metadata = spreadsheet_metadata(service, spreadsheet_id)
    sheets = sheet_map(metadata)
    template_id = sheets.get(TEMPLATE_SHEET_TITLE)
    if template_id is None:
        raise RuntimeError(
            f"Template sheet '{TEMPLATE_SHEET_TITLE}' was not found."
        )

    missing_titles = list(dict.fromkeys(
        method["sheetName"]
        for method in manifest["methods"]
        if method["sheetName"] not in sheets
    ))
    if missing_titles:
        requests = [
            {
                "duplicateSheet": {
                    "sourceSheetId": template_id,
                    "newSheetName": title,
                }
            }
            for title in missing_titles
        ]
        execute_write_with_retry(
            service.spreadsheets().batchUpdate(
                spreadsheetId=spreadsheet_id,
                body={"requests": requests},
            ),
            "creating missing method sheets",
        )
        metadata = spreadsheet_metadata(service, spreadsheet_id)
        sheets = sheet_map(metadata)

    return sheets


def method_list_value_updates(
    service,
    spreadsheet_id: str,
    manifest: dict[str, Any],
) -> list[dict[str, Any]]:
    rows = read_values(
        service,
        spreadsheet_id,
        "methodlist",
        "A1:F200",
    )
    updates: list[dict[str, Any]] = []

    for method in manifest["methods"]:
        target_row = None
        for row_index in range(5, len(rows)):
            if (
                get_cell(rows, row_index, 1) == method["moduleName"]
                and get_cell(rows, row_index, 2) == method["methodName"]
            ):
                target_row = row_index + 1
                break

        if target_row is None:
            placeholder_index = next(
                (
                    row_index
                    for row_index in range(5, len(rows))
                    if get_cell(rows, row_index, 1).casefold()
                    .startswith("modulename")
                    or get_cell(rows, row_index, 2).casefold()
                    .startswith("methodname")
                ),
                None,
            )
            if placeholder_index is not None:
                target_row = placeholder_index + 1

        if target_row is None:
            row_index = 5
            while get_cell(rows, row_index, 0):
                row_index += 1
            target_row = row_index + 1

        row_values = [
            target_row - 5,
            method["moduleName"],
            method["methodName"],
            method["sheetName"],
            method["description"],
            method.get(
                "preCondition",
                "Input variables and mocked states are defined per UTCID.",
            ),
        ]
        updates.append({
            "range": (
                f"{quote_sheet('methodlist')}!A{target_row}:F{target_row}"
            ),
            "majorDimension": "ROWS",
            "values": [row_values],
        })

        while len(rows) < target_row:
            rows.append([])
        rows[target_row - 1] = row_values

    return updates


def statics_value_updates(
    service,
    spreadsheet_id: str,
    manifest: dict[str, Any],
    results: ExecutionResults,
) -> list[dict[str, Any]]:
    metadata = spreadsheet_metadata(service, spreadsheet_id)
    statics_id = sheet_map(metadata)["statics"]
    rows = read_values(service, spreadsheet_id, "statics", "A1:I200")
    pending_methods: list[dict[str, Any]] = []
    assigned: list[tuple[dict[str, Any], int]] = []

    for method in manifest["methods"]:
        target_index = next(
            (
                row_index
                for row_index in range(1, len(rows))
                if get_cell(rows, row_index, 1)
                == method["functionCode"]
            ),
            None,
        )

        if target_index is None:
            target_index = next(
                (
                    row_index
                    for row_index in range(1, len(rows))
                    if get_cell(rows, row_index, 1).casefold()
                    .startswith("methodname")
                ),
                None,
            )

        if target_index is None:
            subtotal_index = next(
                (
                    index
                    for index in range(1, len(rows))
                    if get_cell(rows, index, 1).casefold()
                    == "sub total"
                ),
                len(rows),
            )
            target_index = next(
                (
                    index
                    for index in range(1, subtotal_index)
                    if not get_cell(rows, index, 1)
                ),
                None,
            )

        if target_index is None:
            pending_methods.append(method)
            continue

        assigned.append((method, target_index))
        while len(rows) <= target_index:
            rows.append([])
        rows[target_index] = ["", method["functionCode"]]

    if pending_methods:
        subtotal_index = next(
            (
                index
                for index in range(1, len(rows))
                if get_cell(rows, index, 1).casefold()
                == "sub total"
            ),
            len(rows),
        )
        insert_count = len(pending_methods)
        execute_write_with_retry(
            service.spreadsheets().batchUpdate(
                spreadsheetId=spreadsheet_id,
                body={
                    "requests": [
                        {
                            "insertDimension": {
                                "range": {
                                    "sheetId": statics_id,
                                    "dimension": "ROWS",
                                    "startIndex": subtotal_index,
                                    "endIndex": (
                                        subtotal_index + insert_count
                                    ),
                                },
                                "inheritFromBefore": True,
                            }
                        }
                    ]
                },
            ),
            "inserting rows into statics",
        )
        rows[subtotal_index:subtotal_index] = [
            [] for _ in range(insert_count)
        ]
        for offset, method in enumerate(pending_methods):
            target_index = subtotal_index + offset
            assigned.append((method, target_index))
            rows[target_index] = ["", method["functionCode"]]

    updates: list[dict[str, Any]] = []
    for method, target_index in assigned:
        counts = case_counts(method, results)
        row_values = [
            target_index,
            method["functionCode"],
            counts["P"],
            counts["F"],
            counts["U"],
            counts["N"],
            counts["A"],
            counts["B"],
            len(method["cases"]),
        ]
        updates.append({
            "range": (
                f"{quote_sheet('statics')}!"
                f"A{target_index + 1}:I{target_index + 1}"
            ),
            "majorDimension": "ROWS",
            "values": [row_values],
        })
        rows[target_index] = row_values

    subtotal_index = next(
        (
            index
            for index in range(1, len(rows))
            if get_cell(rows, index, 1).casefold() == "sub total"
        ),
        len(rows),
    )
    subtotal_row = subtotal_index + 1
    last_detail_row = subtotal_row - 1
    updates.append({
        "range": (
            f"{quote_sheet('statics')}!"
            f"A{subtotal_row}:I{subtotal_row}"
        ),
        "majorDimension": "ROWS",
        "values": [[
            "",
            "Sub total",
            f"=SUM(C2:C{last_detail_row})",
            f"=SUM(D2:D{last_detail_row})",
            f"=SUM(E2:E{last_detail_row})",
            f"=SUM(F2:F{last_detail_row})",
            f"=SUM(G2:G{last_detail_row})",
            f"=SUM(H2:H{last_detail_row})",
            f"=SUM(I2:I{last_detail_row})",
        ]],
    })
    return updates


def synchronize_in_batches(
    service,
    spreadsheet_id: str,
    manifest: dict[str, Any],
    results: ExecutionResults,
) -> None:
    sheets = ensure_all_method_sheets(
        service,
        spreadsheet_id,
        manifest,
    )

    value_updates: list[dict[str, Any]] = [{
        "range": f"{quote_sheet('methodlist')}!C1:C3",
        "majorDimension": "ROWS",
        "values": [
            [manifest["projectName"]],
            [manifest["projectCode"]],
            [manifest["testEnvironment"]],
        ],
    }]
    value_updates.extend(
        method_list_value_updates(
            service,
            spreadsheet_id,
            manifest,
        )
    )
    value_updates.extend(
        statics_value_updates(
            service,
            spreadsheet_id,
            manifest,
            results,
        )
    )

    clear_ranges: list[str] = []
    format_requests: list[dict[str, Any]] = []
    for method in manifest["methods"]:
        title = method["sheetName"]
        detail = detail_sheet(manifest, method, results)
        clear_end_column = a1_column(max(26, detail.case_end_column))
        clear_end_row = max(200, detail.result_end_row)
        clear_ranges.append(
            f"{quote_sheet(title)}!A1:"
            f"{clear_end_column}{clear_end_row}"
        )
        value_updates.append({
            "range": f"{quote_sheet(title)}!A1",
            "majorDimension": "ROWS",
            "values": detail.matrix,
        })
        format_requests.extend(
            method_sheet_format_requests(sheets[title], detail)
        )

    if clear_ranges:
        execute_write_with_retry(
            service.spreadsheets().values().batchClear(
                spreadsheetId=spreadsheet_id,
                body={"ranges": clear_ranges},
            ),
            "clearing method sheets",
        )

    execute_write_with_retry(
        service.spreadsheets().values().batchUpdate(
            spreadsheetId=spreadsheet_id,
            body={
                "valueInputOption": "USER_ENTERED",
                "data": value_updates,
            },
        ),
        "writing report values",
    )

    if format_requests:
        execute_write_with_retry(
            service.spreadsheets().batchUpdate(
                spreadsheetId=spreadsheet_id,
                body={"requests": format_requests},
            ),
            "formatting method sheets",
        )


def main() -> int:
    manifest = load_manifest()
    results = read_surefire_results(manifest)
    spreadsheet_id = os.getenv(
        "REPORT51_SPREADSHEET_ID",
        DEFAULT_SPREADSHEET_ID,
    )
    service = sheets_service()

    synchronize_in_batches(
        service,
        spreadsheet_id,
        manifest,
        results,
    )

    report_results = expected_results(manifest, results)
    passed = sum(case.status == "P" for case in report_results)
    failed = sum(case.status == "F" for case in report_results)
    untested = sum(case.status == "U" for case in report_results)
    print(
        "Report 5.1 synchronized: "
        f"passed={passed}, failed={failed}, untested={untested}"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"Report 5.1 synchronization error: {error}", file=sys.stderr)
        raise SystemExit(2)