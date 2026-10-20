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
    def test_native_taps_map_visual_viewport_at_actual_page_scale(self):
        source=(ROOT/'android/tests/src/io/github/himangshurajg/lecture/test/RuntimeChecks.java').read_text()
        self.assertIn('web.getScale()',source)
        self.assertIn('visualViewport?.offsetLeft',source)
        self.assertIn('visualViewport?.offsetTop',source)
        self.assertIn('trusted touch reached',source)
        self.assertNotIn('web.getPaddingTop()+(float)a.getDouble(1)*scale',source)
        # Reproduce the native receipt: x360,y857 missed at CSS137.14,406.52.
        scale, offset_y = 2.625, 80
        self.assertAlmostEqual(360/scale,137.142857,places=5)
        self.assertAlmostEqual(857.1206/scale+offset_y,406.52213,places=3)
        x,y=160*scale,(324.0535888671875-offset_y)*scale
        self.assertAlmostEqual(x,420)
        self.assertAlmostEqual(y,640.64067,places=4)
        self.assertAlmostEqual(y/scale+offset_y,324.0535888671875)
    def test_signing_not_in_source_and_no_private_media(self):
        self.assertNotIn('storepass ',(ROOT/'android/build-test-apk.sh').read_text())
        self.assertFalse(list((ROOT/'android/app/src/main').rglob('*.mp4')))
        self.assertFalse(list((ROOT/'android').rglob('*.keystore')))

if __name__=='__main__': unittest.main()
