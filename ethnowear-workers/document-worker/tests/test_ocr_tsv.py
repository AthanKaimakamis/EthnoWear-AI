import pytest

from ethnowear_document_worker.ocr.tsv import InvalidTsvError, parse_tsv


HEADER = (
    "level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\t"
    "left\ttop\twidth\theight\tconf\ttext"
)


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


def test_parse_tsv_ignores_non_word_empty_and_negative_confidence_rows() -> None:
    tsv = "\n".join([
        HEADER,
        "1\t1\t0\t0\t0\t0\t0\t0\t100\t100\t-1\t",
        "5\t1\t1\t1\t1\t1\t10\t20\t30\t40\t-1\tignored",
        "5\t1\t1\t1\t1\t2\t10\t20\t30\t40\t75\t   ",
    ])

    assert parse_tsv(tsv) == ("", None, ())


def test_parse_tsv_caps_confidence_at_one() -> None:
    tsv = "\n".join([
        HEADER,
        "5\t1\t1\t1\t1\t1\t0\t0\t10\t10\t105\ttext",
    ])

    _, confidence, words = parse_tsv(tsv)

    assert confidence == 1.0
    assert words[0].confidence == 1.0


def test_parse_tsv_accepts_header_only_empty_page() -> None:
    assert parse_tsv(HEADER + "\n") == ("", None, ())


def test_parse_tsv_rejects_missing_columns() -> None:
    with pytest.raises(InvalidTsvError, match="invalid TSV header"):
        parse_tsv("level\ttext\n5\tword")


def test_parse_tsv_rejects_malformed_row_with_sanitized_error() -> None:
    unsafe_value = "/private/tmp/secret-page.png"
    tsv = "\n".join([
        HEADER,
        f"5\t1\t1\t1\t1\t1\t{unsafe_value}\t0\t10\t10\t90\tword",
    ])

    with pytest.raises(InvalidTsvError) as captured:
        parse_tsv(tsv)

    assert str(captured.value) == "OCR output contains an invalid TSV row"
    assert unsafe_value not in str(captured.value)


def test_parse_tsv_rejects_structured_content_inside_word_text() -> None:
    structured_text = "5\t1\t13\t1\t1\t2\t719\t1604\t34\t27\t93.0\tНадпис"
    tsv = "\n".join([
        HEADER,
        f'5\t1\t1\t1\t1\t1\t0\t0\t10\t10\t90\t"{structured_text}"',
    ])

    with pytest.raises(InvalidTsvError, match="invalid TSV row"):
        parse_tsv(tsv)


def test_parse_tsv_rejects_extra_columns() -> None:
    tsv = "\n".join([
        HEADER,
        "5\t1\t1\t1\t1\t1\t0\t0\t10\t10\t90\tword\textra",
    ])

    with pytest.raises(InvalidTsvError, match="invalid TSV row"):
        parse_tsv(tsv)
