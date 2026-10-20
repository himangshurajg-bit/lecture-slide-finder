"""Static Android boundary/packaging regression tests, not emulator tests."""
import pathlib
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
    def test_minimum_and_explicit_test_version(self):
        self.assertEqual(self.manifest.find('uses-sdk').get(ANDROID+'minSdkVersion'),'26')
        self.assertIn('test',self.manifest.get(ANDROID+'versionName'))
    def test_no_javascript_interface_or_file_origin_access(self):
        self.assertNotIn('addJavascriptInterface',self.java)
        for statement in ['setAllowFileAccess(false)', 'setAllowUniversalAccessFromFileURLs(false)', 'setAllowFileAccessFromFileURLs(false)', 'MIXED_CONTENT_NEVER_ALLOW']:
            self.assertIn(statement,self.java)
    def test_origin_checks_and_user_selected_document_exports(self):
        for statement in ['!HOME.equals(url)', 'Intent.ACTION_CREATE_DOCUMENT', 'Intent.ACTION_OPEN_DOCUMENT', 'selectedUris.contains', 'value.length() > 5*1024*1024']:
            self.assertIn(statement,self.java)
        self.assertIn('connect-src \'none\'',self.java)
    def test_signing_not_in_source_and_no_private_media(self):
        self.assertNotIn('storepass ',(ROOT/'android/build-test-apk.sh').read_text())
        self.assertFalse(list((ROOT/'android/app/src/main').rglob('*.mp4')))
        self.assertFalse(list((ROOT/'android').rglob('*.keystore')))

if __name__=='__main__': unittest.main()
