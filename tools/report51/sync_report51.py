from __future__ import annotations

import json
import os
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo

SCOPES = ["https://www.googleapis.com/auth/spreadsheets"]
DEFAULT_SPREADSHEET_ID = "1VX5JxJClV1dsZIEElCP5l73Dw-_YIleTs3KH_iLYo4o"
TEMPLATE_SHEET_TITLE = "extractVideoId"


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
    return json.loads(path.read_text(encoding="utf-8"))


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
) -> dict[str, ExecutedCase]:
    results: dict[str, ExecutedCase] = {}
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

            results[test_name] = executed

    expected_names = {
        case["javaTestName"]
        for method in manifest["methods"]
        for case in method["cases"]
    }
    missing = expected_names.difference(results)
    for test_name in missing:
        results[test_name] = ExecutedCase(
            "U",
            "Test case was not present in the fresh Surefire XML report",
            0,
        )

    return results


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


def ensure_method_sheet(
    service,
    spreadsheet_id: str,
    method_name: str,
) -> None:
    metadata = spreadsheet_metadata(service, spreadsheet_id)
    sheets = sheet_map(metadata)
    if method_name in sheets:
        return

    template_id = sheets.get(TEMPLATE_SHEET_TITLE)
    if template_id is None:
        raise RuntimeError(
            f"Template sheet '{TEMPLATE_SHEET_TITLE}' was not found."
        )

    service.spreadsheets().batchUpdate(
        spreadsheetId=spreadsheet_id,
        body={
            "requests": [
                {
                    "duplicateSheet": {
                        "sourceSheetId": template_id,
                        "newSheetName": method_name,
                    }
                }
            ]
        },
    ).execute()


def clear_values(
    service,
    spreadsheet_id: str,
    sheet_title: str,
    cell_range: str,
) -> None:
    service.spreadsheets().values().clear(
        spreadsheetId=spreadsheet_id,
        range=f"{quote_sheet(sheet_title)}!{cell_range}",
        body={},
    ).execute()


def update_values(
    service,
    spreadsheet_id: str,
    sheet_title: str,
    start_cell: str,
    values: list[list[Any]],
) -> None:
    service.spreadsheets().values().update(
        spreadsheetId=spreadsheet_id,
        range=f"{quote_sheet(sheet_title)}!{start_cell}",
        valueInputOption="USER_ENTERED",
        body={"majorDimension": "ROWS", "values": values},
    ).execute()


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


def write_method_list(
    service,
    spreadsheet_id: str,
    manifest: dict[str, Any],
) -> None:
    rows = read_values(
        service,
        spreadsheet_id,
        "methodlist",
        "A1:F200",
    )

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

        update_values(
            service,
            spreadsheet_id,
            "methodlist",
            f"A{target_row}",
            [[
                target_row - 5,
                method["moduleName"],
                method["methodName"],
                method["sheetName"],
                method["description"],
                method["preCondition"],
            ]],
        )

        while len(rows) < target_row:
            rows.append([])
        rows[target_row - 1] = [
            target_row - 5,
            method["moduleName"],
            method["methodName"],
            method["sheetName"],
            method["description"],
            method["preCondition"],
        ]


def insert_row_before(
    service,
    spreadsheet_id: str,
    sheet_id: int,
    zero_based_row: int,
) -> None:
    service.spreadsheets().batchUpdate(
        spreadsheetId=spreadsheet_id,
        body={
            "requests": [
                {
                    "insertDimension": {
                        "range": {
                            "sheetId": sheet_id,
                            "dimension": "ROWS",
                            "startIndex": zero_based_row,
                            "endIndex": zero_based_row + 1,
                        },
                        "inheritFromBefore": True,
                    }
                }
            ]
        },
    ).execute()


def case_counts(
    method: dict[str, Any],
    results: dict[str, ExecutedCase],
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
        counts[results[case["javaTestName"]].status] += 1
        counts[case["type"]] += 1
    return counts


def write_statics(
    service,
    spreadsheet_id: str,
    manifest: dict[str, Any],
    results: dict[str, ExecutedCase],
) -> None:
    metadata = spreadsheet_metadata(service, spreadsheet_id)
    statics_id = sheet_map(metadata)["statics"]
    rows = read_values(service, spreadsheet_id, "statics", "A1:I200")

    for method in manifest["methods"]:
        target_index = None
        for row_index in range(1, len(rows)):
            if get_cell(rows, row_index, 1) == method["functionCode"]:
                target_index = row_index
                break

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
                    if get_cell(rows, index, 1).casefold() == "sub total"
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
                insert_row_before(
                    service,
                    spreadsheet_id,
                    statics_id,
                    subtotal_index,
                )
                rows.insert(subtotal_index, [])
                target_index = subtotal_index

        counts = case_counts(method, results)
        update_values(
            service,
            spreadsheet_id,
            "statics",
            f"A{target_index + 1}",
            [[
                target_index,
                method["functionCode"],
                counts["P"],
                counts["F"],
                counts["U"],
                counts["N"],
                counts["A"],
                counts["B"],
                len(method["cases"]),
            ]],
        )
        while len(rows) <= target_index:
            rows.append([])
        rows[target_index] = [
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
    update_values(
        service,
        spreadsheet_id,
        "statics",
        f"A{subtotal_row}",
        [[
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
    )


def grouped_case_values(
    method: dict[str, Any],
    value_getter,
) -> list[tuple[str, list[int]]]:
    grouped: dict[str, list[int]] = {}
    for case_index, case in enumerate(method["cases"]):
        value = str(value_getter(case)).strip()
        grouped.setdefault(value, []).append(case_index)
    return list(grouped.items())


def detail_sheet(
    manifest: dict[str, Any],
    method: dict[str, Any],
    results: dict[str, ExecutedCase],
) -> DetailSheet:
    case_count = len(method["cases"])
    case_start_column = 5
    case_end_column = case_start_column + case_count - 1
    column_count = max(18, case_end_column)

    scenario_groups = grouped_case_values(
        method,
        lambda case: case["scenario"],
    )
    input_groups = grouped_case_values(
        method,
        lambda case: case["input"],
    )
    expected_groups = grouped_case_values(
        method,
        lambda case: case["expected"],
    )
    actual_groups = grouped_case_values(
        method,
        lambda case: results[case["javaTestName"]].actual,
    )

    condition_start_row = 8
    precondition_value_row = 9
    scenario_label_row = 10
    scenario_start_row = scenario_label_row + 1
    scenario_end_row = scenario_start_row + len(scenario_groups) - 1
    input_label_row = scenario_end_row + 1
    input_start_row = input_label_row + 1
    input_end_row = input_start_row + len(input_groups) - 1
    condition_end_row = input_end_row

    confirm_start_row = condition_end_row + 1
    expected_start_row = confirm_start_row + 1
    expected_end_row = expected_start_row + len(expected_groups) - 1
    actual_label_row = expected_end_row + 1
    actual_start_row = actual_label_row + 1
    actual_end_row = actual_start_row + len(actual_groups) - 1
    confirm_end_row = actual_end_row

    result_start_row = confirm_end_row + 1
    result_end_row = result_start_row + 4
    matrix: list[list[Any]] = [
        [""] * column_count for _ in range(result_end_row)
    ]

    def put(row: int, column: int, value: Any) -> None:
        matrix[row - 1][column - 1] = value

    def put_group_rows(
        start_row: int,
        groups: list[tuple[str, list[int]]],
    ) -> None:
        for group_index, (value, case_indexes) in enumerate(groups):
            row = start_row + group_index
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
    put(precondition_value_row, 4, method["preCondition"])
    for case_index in range(case_count):
        put(
            precondition_value_row,
            case_start_column + case_index,
            "O",
        )

    put(scenario_label_row, 2, "Scenario")
    put_group_rows(scenario_start_row, scenario_groups)
    put(input_label_row, 2, "Input / mocked state")
    put_group_rows(input_start_row, input_groups)

    put(confirm_start_row, 1, "Confirm")
    put(confirm_start_row, 2, "Expected result")
    put_group_rows(expected_start_row, expected_groups)
    put(actual_label_row, 2, "Actual result")
    put_group_rows(actual_start_row, actual_groups)

    put(result_start_row, 1, "Result")
    put(
        result_start_row,
        2,
        "Type (N: Normal, A: Abnormal, B: Boundary)",
    )
    put(result_start_row + 1, 2, "Passed/Failed")
    put(result_start_row + 2, 2, "Executed Date")
    put(result_start_row + 3, 2, "Duration (seconds)")
    put(result_start_row + 4, 2, "Defect ID")

    for case_index, case in enumerate(method["cases"]):
        column = case_start_column + case_index
        executed = results[case["javaTestName"]]
        put(7, column, case["id"])
        put(result_start_row, column, case["type"])
        put(result_start_row + 1, column, executed.status)
        put(result_start_row + 2, column, executed_date)
        put(
            result_start_row + 3,
            column,
            round(executed.duration_seconds, 3),
        )
        put(result_start_row + 4, column, "")

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
        label_rows=(
            condition_start_row,
            scenario_label_row,
            input_label_row,
            confirm_start_row,
            actual_label_row,
            result_start_row,
            result_start_row + 1,
            result_start_row + 2,
            result_start_row + 3,
            result_start_row + 4,
        ),
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


def format_method_sheet(
    service,
    spreadsheet_id: str,
    sheet_id: int,
    detail: DetailSheet,
) -> None:
    blue = {"red": 0.0, "green": 0.0, "blue": 0.5}
    white = {"red": 1.0, "green": 1.0, "blue": 1.0}
    black = {"red": 0.0, "green": 0.0, "blue": 0.0}
    thin_black_border = {
        "style": "SOLID",
        "color": black,
    }
    table_end_column = max(18, detail.case_end_column)

    requests: list[dict[str, Any]] = [
        {
            "unmergeCells": {
                "range": grid_range(
                    sheet_id,
                    7,
                    100,
                    1,
                    26,
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
                    detail.case_start_column,
                    detail.case_end_column,
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
                        "frozenRowCount": 7,
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

    service.spreadsheets().batchUpdate(
        spreadsheetId=spreadsheet_id,
        body={"requests": requests},
    ).execute()


def write_method_sheets(
    service,
    spreadsheet_id: str,
    manifest: dict[str, Any],
    results: dict[str, ExecutedCase],
) -> None:
    for method in manifest["methods"]:
        title = method["sheetName"]
        ensure_method_sheet(service, spreadsheet_id, title)
        metadata = spreadsheet_metadata(service, spreadsheet_id)
        sheet_id = sheet_map(metadata)[title]
        detail = detail_sheet(manifest, method, results)
        clear_values(service, spreadsheet_id, title, "A1:Z100")
        update_values(
            service,
            spreadsheet_id,
            title,
            "A1",
            detail.matrix,
        )
        format_method_sheet(
            service,
            spreadsheet_id,
            sheet_id,
            detail,
        )


def main() -> int:
    manifest = load_manifest()
    results = read_surefire_results(manifest)
    spreadsheet_id = os.getenv(
        "REPORT51_SPREADSHEET_ID",
        DEFAULT_SPREADSHEET_ID,
    )
    service = sheets_service()

    update_values(
        service,
        spreadsheet_id,
        "methodlist",
        "C1",
        [
            [manifest["projectName"]],
            [manifest["projectCode"]],
            [manifest["testEnvironment"]],
        ],
    )
    write_method_list(service, spreadsheet_id, manifest)
    write_statics(service, spreadsheet_id, manifest, results)
    write_method_sheets(service, spreadsheet_id, manifest, results)

    passed = sum(case.status == "P" for case in results.values())
    failed = sum(case.status == "F" for case in results.values())
    untested = sum(case.status == "U" for case in results.values())
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
