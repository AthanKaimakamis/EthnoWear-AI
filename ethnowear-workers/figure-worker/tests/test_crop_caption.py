import pytest

from ethnowear_figure_worker.crops.caption import (
    extract_printed_figure_number,
)


@pytest.mark.parametrize(
    ("caption", "expected"),
    [
        # Bulgarian
        ("Обр. 12 — Престилка", "12"),
        ("образ № 7а", "7а"),
        ("Фигура 21 - орнамент", "21"),
        ("табл. 4-б", "4-б"),
        ("Снимка 18", "18"),
        ("Илюстрация № 9", "9"),
        ("Диаграма 3.2", "3.2"),
        # English
        ("Fig. 12 — Apron", "12"),
        ("Figure No. 14", "14"),
        ("Figure number 16", "16"),
        ("Plate IV", "IV"),
        ("Illustration 7-B", "7-B"),
        ("Image #25", "25"),
        ("Photo 11", "11"),
        ("Photograph No. 31", "31"),
        ("Diagram 4.2", "4.2"),
        ("Chart 8-A", "8-A"),
        ("Table 6", "6"),
        # Numbers without an explicit figure-like label are not inferred.
        ("Published in 1951", None),
        ("Page 138", None),
        ("Citation 12", None),
        ("12 — Престилка", None),
        ("XII century ornament", None),
        (None, None),
        ("   ", None),
    ],
)
def test_extracts_only_explicit_printed_figure_numbers(
    caption: str | None,
    expected: str | None,
) -> None:
    assert extract_printed_figure_number(
        caption,
        maximum_characters=20,
    ) == expected


def test_rejects_number_above_backend_limit() -> None:
    assert extract_printed_figure_number(
        "Обр. 123",
        maximum_characters=2,
    ) is None


def test_rejects_non_positive_number_limit() -> None:
    with pytest.raises(ValueError, match="length must be positive"):
        extract_printed_figure_number(
            "Обр. 12",
            maximum_characters=0,
        )
