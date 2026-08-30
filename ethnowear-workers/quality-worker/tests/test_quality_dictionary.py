from pathlib import Path

from ethnowear_quality_worker.quality.dictionary import BulgarianDictionary


def test_loads_hunspell_dictionary(tmp_path: Path) -> None:
    path = tmp_path / "bg_BG.dic"
    path.write_text("2\nшевица/AB\nорнамент\n", encoding="utf-8")

    dictionary = BulgarianDictionary.load(path)

    assert dictionary is not None
    assert dictionary.contains("ШЕВИЦА")
    assert dictionary.contains("орнамент")
    assert not dictionary.contains("Аопре3ха")


def test_missing_dictionary_returns_none(tmp_path: Path) -> None:
    assert BulgarianDictionary.load(tmp_path / "missing.dic") is None
