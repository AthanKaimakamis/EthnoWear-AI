import re

_PRINTED_NUMBER_PATTERN = re.compile(
    r"""
    \b
    (?:
        # Bulgarian
        обр(?:аз)?
        |
        фиг(?:ура)?
        |
        табл(?:ица)?
        |
        снимка
        |
        илюстрация
        |
        диаграма
        |
        # English
        fig(?:ure)?
        |
        plate
        |
        illustration
        |
        image
        |
        photo(?:graph)?
        |
        diagram
        |
        chart
        |
        table
    )
    \.?
    \s*
    (?:
        №
        |
        \#
        |
        no\.?
        |
        number
    )?
    \s*
    (?P<number>
        (?:
            [0-9]+
            (?:[.\-–—][0-9]+)*
            (?:[.\-–—]?[A-Za-zА-Яа-я])?
        )
        |
        (?:
            [IVXLCDM]+
        )
    )
    \b
    """,
    flags=re.IGNORECASE | re.VERBOSE,
)


def extract_printed_figure_number(
        caption: str | None,
        *,
        maximum_characters: int,
) -> str | None:
    if maximum_characters <= 0:
        raise ValueError("Maximum printed figure number length must be positive")

    if caption is None or not caption.strip():
        return None

    match = _PRINTED_NUMBER_PATTERN.search(caption)
    if match is None:
        return None

    number = match.group("number").strip()

    if len(number) > maximum_characters:
        return None

    return number
