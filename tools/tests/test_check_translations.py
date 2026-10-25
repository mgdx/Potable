"""Tests of tools/check_translations (run: python3 -m unittest discover tools/tests)."""

import contextlib
import importlib.machinery
import importlib.util
import io
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parent.parent / "check_translations"
_loader = importlib.machinery.SourceFileLoader("check_translations", str(SCRIPT))
_spec = importlib.util.spec_from_loader("check_translations", _loader)
ct = importlib.util.module_from_spec(_spec)
sys.modules["check_translations"] = ct
_loader.exec_module(ct)

ENGLISH = """\
<resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">
    <string name="app_name">Potable</string>
    <string name="action_retry">Retry</string>
    <string name="sampled_on">Sampled on <xliff:g id="date">%1$s</xliff:g></string>
    <string name="error_network">Can\\'t reach Hub\\'Eau.</string>
    <string name="url_repo" translatable="false">https://example.org</string>
    <string-array name="libraries" translatable="false">
        <item>OkHttp</item>
    </string-array>
    <string-array name="themes">
        <item>Light</item>
        <item>Dark %1$s</item>
    </string-array>
    <plurals name="age_days">
        <item quantity="one">%d day ago</item>
        <item quantity="other">%d days ago</item>
    </plurals>
    <plurals name="summary">
        <item quantity="one">%1$d sample since %2$s</item>
        <item quantity="other">%1$d samples since %2$s</item>
    </plurals>
</resources>
"""

# A complete, correct translation body (without the <resources> wrapper).
FR = """\
    <string name="app_name">Potable</string>
    <string name="action_retry">Réessayer</string>
    <string name="sampled_on">Prélevé le %1$s</string>
    <string name="error_network">Impossible de joindre Hub\\'Eau.</string>
    <string-array name="themes">
        <item>Clair</item>
        <item>Sombre %1$s</item>
    </string-array>
    <plurals name="age_days">
        <item quantity="one">Il y a %d jour</item>
        <item quantity="many">Il y a %d jours</item>
        <item quantity="other">Il y a %d jours</item>
    </plurals>
    <plurals name="summary">
        <item quantity="one">%1$d prélèvement depuis le %2$s</item>
        <item quantity="other">%1$d prélèvements depuis le %2$s</item>
    </plurals>
"""

def wrap(body: str) -> str:
    return ('<resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">\n'
            + body + "</resources>\n")


class CheckTranslationsTest(unittest.TestCase):

    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.root = Path(self._tmp.name)
        self.write("values", ENGLISH)

    def tearDown(self):
        self._tmp.cleanup()

    def write(self, folder: str, content: str) -> None:
        path = self.root / "app/src/main/res" / folder / "strings.xml"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")

    def run_check(self, languages=("fr",), fr=None):
        if "fr" in languages:
            self.write("values-fr", fr if fr is not None else wrap(FR))
        return ct.run(self.root, tuple(languages))

    def assertOnlyError(self, report, expected):
        self.assertEqual(len(report.errors), 1, report.errors)
        self.assertIn(expected, report.errors[0])

    # Valid cases

    def test_complete_translations_pass(self):
        report = self.run_check(("fr",))
        self.assertEqual(report.errors, [])
        self.assertEqual(report.warnings, [])

    def test_main_prints_ok_and_returns_zero(self):
        self.run_check(("fr",))
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            code = ct.main(["--root", str(self.root)])
        self.assertEqual(code, 0)
        self.assertIn("check_translations: OK", out.getvalue())

    def test_main_fails_with_code_one_on_missing_files(self):
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            code = ct.main(["--root", str(self.root)])
        self.assertEqual(code, 1)
        self.assertIn("fr: app/src/main/res/values-fr/strings.xml: file not found", out.getvalue())
        self.assertIn("FAILED, 1 error(s)", out.getvalue())

    def test_missing_english_file_fails(self):
        (self.root / "app/src/main/res/values/strings.xml").unlink()
        report = ct.run(self.root, ("fr",))
        self.assertOnlyError(report, "en: app/src/main/res/values/strings.xml: file not found")

    # Missing and extra keys

    def test_missing_string(self):
        report = self.run_check(fr=wrap(FR.replace(
            '    <string name="action_retry">Réessayer</string>\n', "")))
        self.assertOnlyError(report, "fr: action_retry: missing translation (string)")

    def test_missing_plural(self):
        body = FR.split("    <plurals name=\"summary\">")[0]
        report = self.run_check(fr=wrap(body))
        self.assertOnlyError(report, "fr: summary: missing translation (plurals)")

    def test_missing_string_array(self):
        body = FR.replace(FR[FR.index("    <string-array"):FR.index("    <plurals")], "")
        report = self.run_check(fr=wrap(body))
        self.assertOnlyError(report, "fr: themes: missing translation (string-array)")

    def test_key_absent_from_english(self):
        report = self.run_check(fr=wrap(FR + '    <string name="obsolete">Ancien</string>\n'))
        self.assertOnlyError(report, "fr: obsolete: not in the English strings.xml")

    def test_kind_mismatch(self):
        body = FR.replace('<string name="action_retry">Réessayer</string>',
                          '<plurals name="action_retry"><item quantity="other">R</item></plurals>')
        report = self.run_check(fr=wrap(body))
        self.assertOnlyError(report, "fr: action_retry: is a <plurals>, English has a <string>")

    def test_duplicate_name(self):
        report = self.run_check(fr=wrap(FR + '    <string name="action_retry">Encore</string>\n'))
        self.assertOnlyError(report, "fr: action_retry: defined twice")

    # Format arguments

    def test_missing_format_argument(self):
        report = self.run_check(fr=wrap(FR.replace("Prélevé le %1$s", "Prélevé")))
        self.assertOnlyError(report, "fr: sampled_on: format arguments differ: none instead of %1$s")

    def test_wrong_format_conversion(self):
        report = self.run_check(fr=wrap(FR.replace("Prélevé le %1$s", "Prélevé le %1$d")))
        self.assertOnlyError(report, "format arguments differ: %1$d instead of %1$s")

    def test_positional_argument_may_move_and_repeat(self):
        report = self.run_check(fr=wrap(FR.replace("Prélevé le %1$s", "%1$s : prélevé (%1$s)")))
        self.assertEqual(report.errors, [])

    def test_plain_arguments_are_counted(self):
        self.write("values", ENGLISH.replace("Retry", "%s and %s"))
        report = self.run_check(fr=wrap(FR.replace("Réessayer", "%s")))
        self.assertOnlyError(report, "fr: action_retry: format arguments differ: %s instead of %s %s")

    def test_percent_sign_in_french_text_is_not_an_argument(self):
        report = self.run_check(fr=wrap(FR.replace("Réessayer", "Réessayer à 50 % de débit, 100 %%")))
        self.assertEqual(report.errors, [])

    def test_string_array_item_arguments(self):
        report = self.run_check(fr=wrap(FR.replace("Sombre %1$s", "Sombre")))
        self.assertOnlyError(report, "fr: themes: format arguments of item 2 differ")

    def test_string_array_item_count(self):
        report = self.run_check(fr=wrap(FR.replace("        <item>Clair</item>\n", "")))
        self.assertOnlyError(report, "fr: themes: 1 items instead of 2")

    def test_plural_other_item_arguments(self):
        report = self.run_check(fr=wrap(FR.replace("%1$d prélèvements depuis le %2$s",
                                                   "%1$d prélèvements")))
        self.assertOnlyError(report, "fr: summary: format arguments of \"other\" differ")

    def test_plural_many_item_must_keep_count(self):
        report = self.run_check(fr=wrap(FR.replace('"many">Il y a %d jours', '"many">Il y a des jours')))
        self.assertOnlyError(report, "fr: age_days: format arguments of \"many\" differ")

    def test_plural_one_may_omit_the_count(self):
        report = self.run_check(fr=wrap(FR.replace("Il y a %d jour<", "Hier<")))
        self.assertEqual(report.errors, [])

    def test_plural_one_may_not_omit_a_string_argument(self):
        report = self.run_check(fr=wrap(FR.replace("%1$d prélèvement depuis le %2$s",
                                                   "Un prélèvement")))
        self.assertOnlyError(report, "fr: summary: format arguments of \"one\" differ")

    def test_plural_one_may_not_add_an_argument(self):
        report = self.run_check(fr=wrap(FR.replace("Il y a %d jour<", "Il y a %d jour %1$s<")))
        self.assertOnlyError(report, "fr: age_days: format arguments of \"one\" differ")

    def test_plural_without_other(self):
        report = self.run_check(fr=wrap(FR.replace(
            '        <item quantity="other">Il y a %d jours</item>\n', "")))
        self.assertOnlyError(report, "fr: age_days: plural without an \"other\" item")

    def test_unknown_plural_quantity(self):
        report = self.run_check(fr=wrap(FR.replace('quantity="many"', 'quantity="several"')))
        self.assertOnlyError(report, "fr: age_days: unknown plural quantity \"several\"")

    # translatable="false"

    def test_untranslatable_string_must_not_be_translated(self):
        report = self.run_check(fr=wrap(FR + '    <string name="url_repo">https://example.fr</string>\n'))
        self.assertOnlyError(report, "fr: url_repo: translatable=\"false\" in English")

    def test_untranslatable_array_must_not_be_translated(self):
        extra = '    <string-array name="libraries"><item>OkHttp</item></string-array>\n'
        report = self.run_check(fr=wrap(FR + extra))
        self.assertOnlyError(report, "fr: libraries: translatable=\"false\" in English")

    # Malformed XML

    def test_malformed_xml(self):
        report = self.run_check(fr=wrap(FR.replace("Réessayer</string>", "Réessayer</strin>")))
        self.assertOnlyError(report, "fr: app/src/main/res/values-fr/strings.xml: malformed XML")

    def test_unescaped_ampersand_is_malformed(self):
        report = self.run_check(fr=wrap(FR.replace("Réessayer", "Réessayer & revenir")))
        self.assertOnlyError(report, "malformed XML")

    def test_wrong_root_element(self):
        report = self.run_check(fr="<resource>" + FR + "</resource>")
        self.assertOnlyError(report, "root element is <resource>, not <resources>")

    # Apostrophes

    def test_unescaped_apostrophe(self):
        report = self.run_check(fr=wrap(FR.replace("Impossible de joindre", "Impossible d'atteindre")))
        self.assertOnlyError(report, "fr: error_network: unescaped apostrophe")

    def test_unescaped_apostrophe_in_plural_item(self):
        report = self.run_check(fr=wrap(FR.replace("Il y a %d jour<", "Il y a %d jour aujourd'hui<")))
        self.assertOnlyError(report, "fr: age_days: unescaped apostrophe")

    def test_escaped_quoted_and_typographic_apostrophes_pass(self):
        body = FR.replace("Impossible de joindre", "Impossible d\\'atteindre ni d’appeler") \
                 .replace("Réessayer", '"Réessayer aujourd\'hui"')
        report = self.run_check(fr=wrap(body))
        self.assertEqual(report.errors, [])

    def test_english_file_is_checked_too(self):
        self.write("values", ENGLISH.replace("Can\\'t", "Can't"))
        report = self.run_check()
        self.assertOnlyError(report, "en: error_network: unescaped apostrophe")

    # Identical to English (warning only)

    def test_identical_text_is_a_warning(self):
        report = self.run_check(fr=wrap(FR.replace("Réessayer", "Retry")))
        self.assertEqual(report.errors, [])
        self.assertEqual(report.warnings,
                         ['fr: action_retry: warning: identical to English ("Retry")'])

    def test_identical_plural_item_and_array_item_warn(self):
        body = FR.replace("Il y a %d jours<", "%d days ago<").replace("Clair", "Light")
        report = self.run_check(fr=wrap(body))
        self.assertEqual(report.errors, [])
        self.assertEqual(len(report.warnings), 2, report.warnings)
        self.assertTrue(any("themes[1]" in w for w in report.warnings))
        self.assertTrue(any("age_days[other]" in w for w in report.warnings))

    def test_allow_listed_terms_do_not_warn(self):
        # app_name "Potable" is identical in FR already; add pH and Hub'Eau.
        self.write("values", ENGLISH.replace("Retry", "pH, ARS"))
        report = self.run_check(fr=wrap(FR.replace("Réessayer", "pH, ARS")))
        self.assertEqual(report.warnings, [])

    def test_language_allow_listed_terms_do_not_warn_in_that_language(self):
        self.write("values", ENGLISH.replace("Retry", "Nitrates"))
        report = self.run_check(fr=wrap(FR.replace("Réessayer", "Nitrates")))
        self.assertEqual(report.warnings, [])

    def test_language_allow_listed_terms_warn_in_other_languages(self):
        # "Nitrates" is allowed in French, not in a language without a list.
        self.assertEqual(ct.remainder_after_allowed("Nitrates", "fr"), "")
        self.assertEqual(ct.remainder_after_allowed("Nitrates", "en"), "Nitrates")

    def test_no_break_space_does_not_hide_an_identical_text(self):
        self.write("values", ENGLISH.replace("Retry", "Retry %1$s"))
        for space in (" ", "\\u00A0", " "):
            with self.subTest(space=space):
                body = FR.replace("Réessayer", f"Retry{space}%1$s")
                report = self.run_check(fr=wrap(body))
                self.assertEqual(report.errors, [])
                self.assertEqual(len(report.warnings), 1, report.warnings)
                self.assertIn("action_retry", report.warnings[0])


class RepositoryTest(unittest.TestCase):

    def test_repository_english_file_is_clean(self):
        english = SCRIPT.parent.parent / "app/src/main/res/values/strings.xml"
        report = ct.Report()
        resources = ct.parse_file(english, "en", report)
        self.assertEqual(report.errors, [])
        self.assertTrue(resources)


class FormatArgsTest(unittest.TestCase):

    def test_tokens(self):
        args = ct.format_args("%1$s +%2$d, %d %s %.1f %% %n")
        self.assertEqual(sorted(args.elements()), sorted(["%1$s", "%2$d", "%f", "%d", "%s"]))


if __name__ == "__main__":
    unittest.main()
