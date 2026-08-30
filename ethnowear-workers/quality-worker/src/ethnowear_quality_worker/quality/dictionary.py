from pathlib import Path


class BulgarianDictionary:
    def __init__(self, words: frozenset[str]) -> None:
        self._words = words

    @classmethod
    def load(cls, path: Path) -> "BulgarianDictionary | None":
        try:
            lines = path.read_text(encoding="utf-8-sig").splitlines()
        except (OSError, UnicodeError):
            return None

        if lines and lines[0].strip().isdigit():
            lines = lines[1:]

        words = frozenset(
            entry.split("/", 1)[0].strip().casefold()
            for entry in lines
            if entry.strip() and not entry.lstrip().startswith("#")
        )
        return cls(words) if words else None

    def contains(self, word: str) -> bool:
        return word.casefold() in self._words
