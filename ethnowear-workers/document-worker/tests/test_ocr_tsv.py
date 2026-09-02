import pytest

from ethnowear_document_worker.ocr.tsv import (
    InvalidTsvError,
    parse_tsv,
    parse_tsv_metadata_with_diagnostics,
)


HEADER = (
    "level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\t"
    "left\ttop\twidth\theight\tconf\ttext"
)


def word(text: str, *, number: int = 1, confidence: str = "90") -> str:
    return f"5\t1\t1\t1\t1\t{number}\t10\t20\t30\t40\t{confidence}\t{text}"


def test_parse_tsv_extracts_bulgarian_lines_and_layout() -> None:
    tsv = "\n".join([
        HEADER,
        "5\t1\t1\t1\t1\t1\t10\t20\t70\t30\t96.5\tБългарска",
        "5\t1\t1\t1\t1\t2\t90\t20\t60\t30\t83.5\tшевица",
        "5\t1\t1\t1\t2\t1\t10\t60\t80\t30\t90\tВтори ред",
    ])

    raw_text, confidence, words = parse_tsv(tsv)

    assert raw_text == "Българска шевица\nВтори ред"
    assert confidence == pytest.approx(0.9)
    assert len(words) == 3
    assert words[0].confidence == pytest.approx(0.965)
    assert (words[0].left, words[0].top, words[0].width, words[0].height) == (
        10, 20, 70, 30
    )


def test_parse_tsv_does_not_treat_unescaped_quote_as_csv_syntax() -> None:
    tsv = "\n".join([
        HEADER,
        word('"', number=1),
        word("следващ", number=2),
        word("ред", number=3),
    ])

    raw_text, _, words = parse_tsv(tsv)

    assert [item.text for item in words] == ['"', "следващ", "ред"]
    assert raw_text == '" следващ ред'


def test_parse_tsv_skips_one_malformed_row_among_valid_rows() -> None:
    tsv = "\n".join([
        HEADER,
        word("валидна", number=1),
        "5\t1\t1\t1\t1\t2\tbad\t20\t30\t40\t90\tтайна",
        word("дума", number=3),
    ])

    confidence, words, diagnostics = parse_tsv_metadata_with_diagnostics(tsv)

    assert confidence == pytest.approx(0.9)
    assert [item.text for item in words] == ["валидна", "дума"]
    assert diagnostics.rejected_row_count == 1
    assert diagnostics.rejection_reasons == (("numeric_value", 1),)


def test_parse_tsv_rejects_missing_required_fields() -> None:
    with pytest.raises(InvalidTsvError) as captured:
        parse_tsv("level\ttext\n5\tword")

    assert captured.value.category == "invalid_header"


def test_parse_tsv_skips_invalid_numeric_value_without_exposing_it() -> None:
    unsafe_value = "/private/tmp/secret-page.png"
    tsv = "\n".join([
        HEADER,
        word("валидна", number=1),
        f"5\t1\t1\t1\t1\t2\t{unsafe_value}\t0\t10\t10\t90\tтайна",
        word("дума", number=3),
    ])

    _, words, diagnostics = parse_tsv_metadata_with_diagnostics(tsv)

    assert [item.text for item in words] == ["валидна", "дума"]
    assert diagnostics.rejection_reasons == (("numeric_value", 1),)
    assert unsafe_value not in repr(diagnostics)


def test_parse_tsv_skips_embedded_tab_and_never_returns_tsv_as_text() -> None:
    tsv = "\n".join([
        HEADER,
        word("валидна", number=1),
        word("5\t1\t13\t1\t1\t2\t719\t1604\t34\t27\t93.0\tНадпис", number=2),
        word("дума", number=3),
    ])

    raw_text, _, words = parse_tsv(tsv)

    assert raw_text == "валидна дума"
    assert [item.text for item in words] == ["валидна", "дума"]
    assert "719\t1604" not in raw_text


@pytest.mark.parametrize("tsv", ["", "not-a-tsv-header"])
def test_parse_tsv_rejects_empty_or_invalid_header(tsv: str) -> None:
    with pytest.raises(InvalidTsvError) as captured:
        parse_tsv(tsv)

    assert captured.value.category in {"missing_header", "invalid_header"}


def test_parse_tsv_rejects_excessive_malformed_rows() -> None:
    malformed = [
        f"5\t1\t1\t1\t1\t{index}\tbad\t0\t10\t10\t90\tbad"
        for index in range(1, 12)
    ]
    valid = [word(f"word-{index}", number=index) for index in range(12, 42)]

    with pytest.raises(InvalidTsvError) as captured:
        parse_tsv("\n".join([HEADER, *malformed, *valid]))

    assert captured.value.category == "excessive_malformed_rows"
    assert captured.value.rejected_row_count == 11
    assert captured.value.usable_word_count == 30


@pytest.mark.parametrize(
    "rows",
    [
        [],
        ["1\t1\t0\t0\t0\t0\t0\t0\t100\t100\t-1\t"],
        [word("ignored", confidence="-1")],
        [word("bad", confidence="not-a-number")],
    ],
)
def test_parse_tsv_rejects_output_without_usable_words(rows: list[str]) -> None:
    with pytest.raises(InvalidTsvError) as captured:
        parse_tsv("\n".join([HEADER, *rows]))

    assert captured.value.category == "no_usable_words"


def test_parse_tsv_caps_confidence_at_one() -> None:
    _, confidence, words = parse_tsv("\n".join([HEADER, word("text", confidence="105")]))

    assert confidence == 1.0
    assert words[0].confidence == 1.0
