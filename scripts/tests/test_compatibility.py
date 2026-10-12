import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("compatibility", Path(__file__).parents[1] / "test-compatibility.py")
compatibility = importlib.util.module_from_spec(spec)
spec.loader.exec_module(compatibility)


def transcript(names, codes=None, completion=True):
    text = ""
    for name, code in zip(names, codes or [0] * len(names)):
        for status in [1, code]:
            text += f"INSTRUMENTATION_STATUS: class=example.FlowTest\nINSTRUMENTATION_STATUS: test={name}\nINSTRUMENTATION_STATUS_CODE: {status}\n"
    if completion:
        text += f"\nOK ({len(names)} tests)\nINSTRUMENTATION_CODE: -1\n"
    return text


class ResultValidationTest(unittest.TestCase):
    def report(self, output, code=0):
        return compatibility.parse_instrumentation(output, {("example.FlowTest", "first"), ("example.FlowTest", "second")}, code)

    def test_all_expected_tests_must_finish_successfully(self):
        self.assertEqual("PASS", self.report(transcript(["first", "second"]))["status"])

    def test_adb_zero_does_not_hide_a_crash(self):
        report = self.report(transcript(["first"], completion=False) + "INSTRUMENTATION_RESULT: shortMsg=Process crashed.\n")
        self.assertEqual("FAIL", report["status"])
        self.assertEqual([("example.FlowTest", "second")], report["missing"])

    def test_missing_unexpected_duplicate_and_empty_results_fail(self):
        for names in [[], ["first"], ["first", "third"], ["first", "second", "second"]]:
            with self.subTest(names=names):
                self.assertEqual("FAIL", self.report(transcript(names))["status"])

    def test_errors_failures_ignored_and_assumptions_fail(self):
        for code in [-1, -2, -3, -4]:
            with self.subTest(code=code):
                self.assertEqual("FAIL", self.report(transcript(["first", "second"], [0, code]))["status"])

    def test_failed_command_and_missing_completion_fail(self):
        self.assertEqual("FAIL", self.report(transcript(["first", "second"]), 1)["status"])
        self.assertEqual("FAIL", self.report(transcript(["first", "second"], completion=False))["status"])

    def test_widget_progress_status_is_not_a_finished_test(self):
        progress = "INSTRUMENTATION_STATUS: adaptive_case=LIGHT 148x156\nINSTRUMENTATION_STATUS_CODE: 0\n"
        self.assertEqual("PASS", self.report(progress + transcript(["first", "second"]))["status"])
        self.assertEqual("FAIL", self.report(progress)["status"])

    def test_core_selection_is_a_nonempty_subset_of_full_source_inventory(self):
        core = compatibility.expected_tests(compatibility.CORE)
        full = compatibility.expected_tests(compatibility.FULL)
        self.assertTrue(core)
        self.assertLess(core, full)
        self.assertLess(compatibility.expected_tests(compatibility.LAYOUT), full)


if __name__ == "__main__":
    unittest.main()
