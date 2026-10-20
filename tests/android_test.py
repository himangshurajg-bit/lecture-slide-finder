"""Static Android boundary/packaging regression tests, not emulator tests."""
import pathlib
import re
import unittest
import xml.etree.ElementTree as ET
ROOT=pathlib.Path(__file__).resolve().parents[1]
ANDROID='{http://schemas.android.com/apk/res/android}'

class AndroidBoundaryTests(unittest.TestCase):
    def setUp(self):
        self.manifest=ET.parse(ROOT/'android/app/src/main/AndroidManifest.xml').getroot()
        self.java=(ROOT/'android/app/src/main/java/io/github/himangshurajg/lecture/MainActivity.java').read_text()
    def test_no_network_or_broad_storage_permissions(self):
        self.assertEqual(self.manifest.findall('uses-permission'),[])
        self.assertEqual(self.manifest.find('application').get(ANDROID+'allowBackup'),'false')
    def test_minimum_and_release_candidate_version(self):
        self.assertEqual(self.manifest.find('uses-sdk').get(ANDROID+'minSdkVersion'),'26')
        self.assertEqual(self.manifest.get(ANDROID+'versionName'),'1.00.0.00 stable')
        self.assertEqual(self.manifest.find('application').get(ANDROID+'debuggable'),'false')
        self.assertEqual(self.manifest.get(ANDROID+'versionCode'),'2')
    def test_no_javascript_interface_or_file_origin_access(self):
        self.assertNotIn('addJavascriptInterface',self.java)
        for statement in ['setAllowFileAccess(false)', 'setAllowUniversalAccessFromFileURLs(false)', 'setAllowFileAccessFromFileURLs(false)', 'MIXED_CONTENT_NEVER_ALLOW']:
            self.assertIn(statement,self.java)
    def test_origin_checks_and_user_selected_document_exports(self):
        for statement in ['!HOME.equals(url)', 'Intent.ACTION_CREATE_DOCUMENT', 'Intent.ACTION_OPEN_DOCUMENT', 'selectedUris.contains', 'value.length() > 5*1024*1024']:
            self.assertIn(statement,self.java)
        self.assertIn("connect-src 'self' blob: data:",self.java)
        self.assertIn("worker-src 'self'",self.java)
        self.assertIn('!HOST.equals(u.getHost())',self.java)
        self.assertNotIn('connect-src *',self.java)
    def test_native_ci_references_only_present_instrumentation(self):
        runner=(ROOT/'android/ci/run-api36.py').read_text()
        for name in set(re.findall(r"'([A-Z][A-Za-z]+Checks)'", runner)):
            source=ROOT/'android/tests/src/io/github/himangshurajg/lecture/test'/(name+'.java')
            self.assertTrue(source.is_file(), name+' missing from clean checkout')
        build=(ROOT/'android/tests/run.sh').read_text()
        self.assertIn('rm -rf "$OUT/classes" "$OUT/dex"',build)
    def test_native_taps_account_for_webview_insets(self):
        source=(ROOT/'android/tests/src/io/github/himangshurajg/lecture/test/RuntimeChecks.java').read_text()
        self.assertIn('web.getPaddingLeft()+(float)a.getDouble(0)*scale',source)
        self.assertIn('web.getPaddingTop()+(float)a.getDouble(1)*scale',source)
        self.assertIn('WEB_TOUCH_RECEIPT',source)
    def test_signing_not_in_source_and_no_private_media(self):
        self.assertNotIn('storepass ',(ROOT/'android/build-test-apk.sh').read_text())
        self.assertFalse(list((ROOT/'android/app/src/main').rglob('*.mp4')))
        self.assertFalse(list((ROOT/'android').rglob('*.keystore')))

if __name__=='__main__': unittest.main()
