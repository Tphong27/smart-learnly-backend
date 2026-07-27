from __future__ import annotations

import json
import os
from pathlib import Path
from typing import Any

SCOPES = ["https://www.googleapis.com/auth/spreadsheets"]
DEFAULT_SPREADSHEET_ID = "1VX5JxJClV1dsZIEElCP5l73Dw-_YIleTs3KH_iLYo4o"
SCOPE_SHEET_TITLE = "Khiem-Scope"
SCOPE_COLUMN_COUNT = 12


def manifest_path() -> Path:
    return Path(__file__).with_name("khiem-scope-manifest.json")


def load_manifest() -> dict[str, Any]:
    return json.loads(manifest_path().read_text(encoding="utf-8"))


def sheets_service():
    from google.oauth2 import service_account
    from googleapiclient.discovery import build

    credentials_path = os.getenv("GOOGLE_APPLICATION_CREDENTIALS")
    if not credentials_path:
        raise RuntimeError("GOOGLE_APPLICATION_CREDENTIALS is not set.")

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
            fields=(
                "spreadsheetId,properties.title,"
                "sheets.properties,sheets.basicFilter"
            ),
        )
        .execute()
    )


def sheet_properties(
    metadata: dict[str, Any],
    title: str,
) -> dict[str, Any] | None:
    for sheet in metadata.get("sheets", []):
        properties = sheet.get("properties", {})
        if properties.get("title") == title:
            return properties
    return None


def ensure_scope_sheet(
    service,
    spreadsheet_id: str,
    required_rows: int,
) -> int:
    metadata = spreadsheet_metadata(service, spreadsheet_id)
    properties = sheet_properties(metadata, SCOPE_SHEET_TITLE)
    if properties is None:
        response = (
            service.spreadsheets()
            .batchUpdate(
                spreadsheetId=spreadsheet_id,
                body={
                    "requests": [
                        {
                            "addSheet": {
                                "properties": {
                                    "title": SCOPE_SHEET_TITLE,
                                    "gridProperties": {
                                        "rowCount": max(required_rows, 300),
                                        "columnCount": SCOPE_COLUMN_COUNT,
                                        "frozenRowCount": 5,
                                    },
                                }
                            }
                        }
                    ]
                },
            )
            .execute()
        )
        return int(response["replies"][0]["addSheet"]["properties"]["sheetId"])

    sheet_id = int(properties["sheetId"])
    grid = properties.get("gridProperties", {})
    requests: list[dict[str, Any]] = []
    if int(grid.get("rowCount", 0)) < required_rows:
        requests.append(
            {
                "updateSheetProperties": {
                    "properties": {
                        "sheetId": sheet_id,
                        "gridProperties": {"rowCount": required_rows},
                    },
                    "fields": "gridProperties.rowCount",
                }
            }
        )
    if int(grid.get("columnCount", 0)) < SCOPE_COLUMN_COUNT:
        requests.append(
            {
                "updateSheetProperties": {
                    "properties": {
                        "sheetId": sheet_id,
                        "gridProperties": {
                            "columnCount": SCOPE_COLUMN_COUNT
                        },
                    },
                    "fields": "gridProperties.columnCount",
                }
            }
        )
    if requests:
        (
            service.spreadsheets()
            .batchUpdate(
                spreadsheetId=spreadsheet_id,
                body={"requests": requests},
            )
            .execute()
        )
    return sheet_id


def scope_rows(manifest: dict[str, Any]) -> list[list[Any]]:
    summary = manifest["summary"]
    rows: list[list[Any]] = [
        ["KHIEM UNIT-TEST SCOPE"] + [""] * 11,
        [
            "WBS source",
            summary["wbsSource"],
            "Screens",
            summary["screenCount"],
            "Selected files",
            summary["selectedFileCount"],
            "Excluded files",
            summary["excludedFileCount"],
            "Selected functions",
            summary["selectedFunctionCount"],
            "",
            "",
        ],
        [
            "Backend branch",
            summary["backendBranch"],
            "Frontend branch",
            summary["frontendBranch"],
            "Selection rule",
            "Up to 3 most complex real functions per testable file",
            "",
            "",
            "",
            "",
            "",
            "",
        ],
        [
            "Result policy",
            (
                "Only real JUnit/Vitest XML or JSON results may set P/F/U; "
                "the script never pre-fills Passed."
            ),
        ]
        + [""] * 10,
        [
            "Repository",
            "Screens",
            "File",
            "Status",
            "Rank",
            "Selected Function",
            "Signature",
            "Sheet Name",
            "Method Key",
            "Complexity Score",
            "Cyclomatic Approx",
            "Reason",
        ],
    ]

    for item in manifest["scope"]:
        functions = item.get("selectedFunctions", [])
        if not functions:
            rows.append(
                [
                    item["repository"],
                    "; ".join(item.get("screens", [])),
                    item["file"],
                    item["status"],
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    item["reason"],
                ]
            )
            continue

        for function in functions:
            rows.append(
                [
                    item["repository"],
                    "; ".join(item.get("screens", [])),
                    item["file"],
                    item["status"],
                    function["rank"],
                    function["name"],
                    function["signature"],
                    function["sheetName"],
                    function["methodKey"],
                    function["score"],
                    function["cyclomaticApprox"],
                    item["reason"],
                ]
            )

    return rows


def write_scope_values(
    service,
    spreadsheet_id: str,
    rows: list[list[Any]],
) -> None:
    scope_range = f"'{SCOPE_SHEET_TITLE}'!A1:L{max(len(rows), 300)}"
    (
        service.spreadsheets()
        .values()
        .clear(
            spreadsheetId=spreadsheet_id,
            range=scope_range,
            body={},
        )
        .execute()
    )
    (
        service.spreadsheets()
        .values()
        .update(
            spreadsheetId=spreadsheet_id,
            range=f"'{SCOPE_SHEET_TITLE}'!A1",
            valueInputOption="USER_ENTERED",
            body={"majorDimension": "ROWS", "values": rows},
        )
        .execute()
    )


def format_scope_sheet(
    service,
    spreadsheet_id: str,
    sheet_id: int,
    row_count: int,
) -> None:
    navy = {"red": 0.2, "green": 0.2, "blue": 0.6}
    white = {"red": 1, "green": 1, "blue": 1}
    requests: list[dict[str, Any]] = [
        {
            "unmergeCells": {
                "range": {
                    "sheetId": sheet_id,
                    "startRowIndex": 0,
                    "endRowIndex": 1,
                    "startColumnIndex": 0,
                    "endColumnIndex": SCOPE_COLUMN_COUNT,
                }
            }
        },
        {
            "mergeCells": {
                "range": {
                    "sheetId": sheet_id,
                    "startRowIndex": 0,
                    "endRowIndex": 1,
                    "startColumnIndex": 0,
                    "endColumnIndex": SCOPE_COLUMN_COUNT,
                },
                "mergeType": "MERGE_ALL",
            }
        },
        {
            "repeatCell": {
                "range": {
                    "sheetId": sheet_id,
                    "startRowIndex": 0,
                    "endRowIndex": 1,
                    "startColumnIndex": 0,
                    "endColumnIndex": SCOPE_COLUMN_COUNT,
                },
                "cell": {
                    "userEnteredFormat": {
                        "backgroundColor": navy,
                        "horizontalAlignment": "CENTER",
                        "verticalAlignment": "MIDDLE",
                        "textFormat": {
                            "foregroundColor": white,
                            "bold": True,
                            "fontSize": 14,
                        },
                    }
                },
                "fields": (
                    "userEnteredFormat(backgroundColor,"
                    "horizontalAlignment,verticalAlignment,textFormat)"
                ),
            }
        },
        {
            "repeatCell": {
                "range": {
                    "sheetId": sheet_id,
                    "startRowIndex": 4,
                    "endRowIndex": 5,
                    "startColumnIndex": 0,
                    "endColumnIndex": SCOPE_COLUMN_COUNT,
                },
                "cell": {
                    "userEnteredFormat": {
                        "backgroundColor": navy,
                        "horizontalAlignment": "CENTER",
                        "verticalAlignment": "MIDDLE",
                        "wrapStrategy": "WRAP",
                        "textFormat": {
                            "foregroundColor": white,
                            "bold": True,
                            "fontSize": 10,
                        },
                    }
                },
                "fields": (
                    "userEnteredFormat(backgroundColor,"
                    "horizontalAlignment,verticalAlignment,"
                    "wrapStrategy,textFormat)"
                ),
            }
        },
        {
            "repeatCell": {
                "range": {
                    "sheetId": sheet_id,
                    "startRowIndex": 1,
                    "endRowIndex": row_count,
                    "startColumnIndex": 0,
                    "endColumnIndex": SCOPE_COLUMN_COUNT,
                },
                "cell": {
                    "userEnteredFormat": {
                        "verticalAlignment": "TOP",
                        "wrapStrategy": "WRAP",
                        "textFormat": {
                            "fontFamily": "Tahoma",
                            "fontSize": 9,
                        },
                    }
                },
                "fields": (
                    "userEnteredFormat("
                    "verticalAlignment,wrapStrategy,textFormat)"
                ),
            }
        },
        {
            "setBasicFilter": {
                "filter": {
                    "range": {
                        "sheetId": sheet_id,
                        "startRowIndex": 4,
                        "endRowIndex": row_count,
                        "startColumnIndex": 0,
                        "endColumnIndex": SCOPE_COLUMN_COUNT,
                    }
                }
            }
        },
        {
            "updateSheetProperties": {
                "properties": {
                    "sheetId": sheet_id,
                    "gridProperties": {"frozenRowCount": 5},
                },
                "fields": "gridProperties.frozenRowCount",
            }
        },
    ]

    widths = [100, 240, 520, 100, 55, 190, 280, 260, 340, 100, 115, 440]
    for column_index, pixel_size in enumerate(widths):
        requests.append(
            {
                "updateDimensionProperties": {
                    "range": {
                        "sheetId": sheet_id,
                        "dimension": "COLUMNS",
                        "startIndex": column_index,
                        "endIndex": column_index + 1,
                    },
                    "properties": {"pixelSize": pixel_size},
                    "fields": "pixelSize",
                }
            }
        )

    (
        service.spreadsheets()
        .batchUpdate(
            spreadsheetId=spreadsheet_id,
            body={"requests": requests},
        )
        .execute()
    )


def get_cell(rows: list[list[Any]], row: int, column: int) -> str:
    if row >= len(rows) or column >= len(rows[row]):
        return ""
    return str(rows[row][column]).strip()


def upsert_method_list(
    service,
    spreadsheet_id: str,
    manifest: dict[str, Any],
) -> None:
    response = (
        service.spreadsheets()
        .values()
        .get(
            spreadsheetId=spreadsheet_id,
            range="'methodlist'!A1:F1000",
            valueRenderOption="FORMATTED_VALUE",
        )
        .execute()
    )
    rows = response.get("values", [])
    while len(rows) < 1000:
        rows.append([])

    next_row = 6
    while next_row <= len(rows) and any(
        get_cell(rows, next_row - 1, column)
        for column in range(6)
    ):
        next_row += 1

    updates: list[dict[str, Any]] = []
    sequence = 1
    for item in manifest["scope"]:
        if item["status"] != "Selected":
            continue
        module_name = (
            ("BE:" if item["repository"] == "backend" else "FE:")
            + Path(item["file"]).stem
        )
        for function in item["selectedFunctions"]:
            signature = function["signature"]
            target_row = None
            for row_index in range(5, len(rows)):
                if (
                    get_cell(rows, row_index, 1) == module_name
                    and get_cell(rows, row_index, 2) == signature
                ):
                    target_row = row_index + 1
                    break

            if target_row is None:
                target_row = next_row
                next_row += 1

            values = [
                sequence,
                module_name,
                signature,
                function["sheetName"],
                (
                    f"Top-{function['rank']} complexity function in "
                    f"{item['file']}; WBS: {', '.join(item['screens'])}."
                ),
                (
                    "Use real JUnit/Vitest behavior tests. Status remains "
                    "Untested until a fresh result artifact contains this method."
                ),
            ]
            updates.append(
                {
                    "range": f"'methodlist'!A{target_row}:F{target_row}",
                    "values": [values],
                }
            )
            rows[target_row - 1] = values
            sequence += 1

    (
        service.spreadsheets()
        .values()
        .batchUpdate(
            spreadsheetId=spreadsheet_id,
            body={
                "valueInputOption": "USER_ENTERED",
                "data": updates,
            },
        )
        .execute()
    )


def main() -> int:
    manifest = load_manifest()
    spreadsheet_id = os.getenv(
        "REPORT51_SPREADSHEET_ID",
        DEFAULT_SPREADSHEET_ID,
    ).strip()
    if not spreadsheet_id:
        raise RuntimeError("REPORT51_SPREADSHEET_ID is empty.")

    rows = scope_rows(manifest)
    service = sheets_service()
    sheet_id = ensure_scope_sheet(
        service,
        spreadsheet_id,
        required_rows=max(len(rows), 300),
    )
    write_scope_values(service, spreadsheet_id, rows)
    format_scope_sheet(service, spreadsheet_id, sheet_id, len(rows))
    upsert_method_list(service, spreadsheet_id, manifest)

    print(
        "Khiem scope synchronized: "
        f"files={manifest['summary']['selectedFileCount']}, "
        f"functions={manifest['summary']['selectedFunctionCount']}, "
        f"rows={len(rows)}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
