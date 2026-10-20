#!/usr/bin/env python3
"""Run candidate checks, refusing to treat adb/instrument exit status as test success."""
import subprocess,pathlib,json,time,hashlib,sys
focus="--frames-only" in sys.argv
root=pathlib.Path.cwd(); out=root/'android/runtime/ci-evidence';out.mkdir(parents=True,exist_ok=True)
results=[]
# Retain device diagnostics even if the emulator exits during playback.
live=open(out/'live-logcat.log','wb')
logger=subprocess.Popen(['adb','logcat','-v','threadtime'],stdout=live,stderr=live)
def cmd(args,name,timeout=45,required=True):
    try:
        p=subprocess.run(args,capture_output=True,timeout=timeout)
        (out/(name+'.log')).write_bytes(p.stdout+p.stderr)
        if required and p.returncode:raise RuntimeError(name+' returned '+str(p.returncode))
        return p.stdout.decode(errors='replace')
    except subprocess.TimeoutExpired as e:
        (out/(name+'.log')).write_bytes((e.stdout or b'')+(e.stderr or b'')+b'\nTIMEOUT\n')
        raise RuntimeError(name+' timed out')
def capture(name):
    try:
        p=subprocess.run(['adb','exec-out','screencap','-p'],capture_output=True,timeout=10)
        if p.returncode==0:(out/(name+'.png')).write_bytes(p.stdout)
        cmd(['adb','shell','uiautomator','dump','/sdcard/window.xml'],name+'-dump',20,False)
        cmd(['adb','pull','/sdcard/window.xml',str(out/(name+'.xml'))],name+'-xml',10,False)
    except Exception:pass
try:
    assert cmd(['adb','shell','getprop','sys.boot_completed'],'boot').strip()=='1'
    assert cmd(['adb','shell','getprop','ro.build.version.sdk'],'sdk').strip()=='36'
    cmd(['adb','shell','getprop'],'properties')
    cmd(['adb','install','-r','android/build/lecture-slide-finder-1.00.0.00-stable.apk'],'install-candidate',90)
    cmd(['adb','install','-r','android/runtime/instrumentation/runtime-checks.apk'],'install-instrumentation',90)
    cmd(['adb','shell','mkdir','-p','/sdcard/Download'],'download-dir')
    files={'frame-study.mp4':root/'tests/fixtures/frame-study.mp4','lecture.mp4':root/'tests/fixtures/lecture.mp4','lecture.srt':root/'android/ci/fixtures/lecture.srt','speech-en.mp4':root/'android/ci/fixtures/speech-en.mp4','speech-hi.mp4':root/'android/ci/fixtures/speech-hi.mp4'}
    for name,path in files.items():
        cmd(['adb','push',str(path),'/sdcard/Download/'+name],'push-'+name)
        cmd(['adb','shell','am','broadcast','-a','android.intent.action.MEDIA_SCANNER_SCAN_FILE','-d','file:///sdcard/Download/'+name],'scan-'+name)
    checks=[('frame-study','FrameStudyChecks','ALL_FRAME_STUDY_CHECKS_PASSED',[]),('handled-errors','ErrorChecks','ALL_ERROR_CHECKS_PASSED',[]),('engine-en','AsrChecks','PASS NATIVE_ASR_PIPELINE_AND_PRE_CANCEL en',['-e','language','en']),('engine-hi','AsrChecks','PASS NATIVE_ASR_PIPELINE_AND_PRE_CANCEL hi',['-e','language','hi','-e','fixture','hi-full.m4a']),('documents','RuntimeChecks','ALL_CHECKS_PASSED',[]),('speech-ui-en','AsrUiChecks','ALL_ASR_UI_CHECKS_PASSED en',['-e','language','en']),('speech-ui-hi','AsrUiChecks','ALL_ASR_UI_CHECKS_PASSED hi',['-e','language','hi']),('lifecycle-errors','EdgeChecks','ALL_EDGE_CHECKS_PASSED',[])]
    if focus:checks=[c for c in checks if c[0]=='frame-study']
    for name,klass,marker,extras in checks:
        cmd(['adb','shell','am','force-stop','io.github.himangshurajg.lecture'],name+'-stop',10,False)
        start=time.monotonic()
        try:
            text=cmd(['adb','shell','am','instrument','-w']+extras+['io.github.himangshurajg.lecture.test/.'+klass],name,330)
            passed=marker in text and 'FAIL ' not in text and 'INSTRUMENTATION_FAILED' not in text
            results.append({'check':name,'passed':passed,'elapsed_seconds':round(time.monotonic()-start,2)})
        except Exception as e:results.append({'check':name,'passed':False,'error':str(e)})
        capture(name)
    # Verify native export contents, not just a successful Save action.
    text=cmd(['adb','shell','find','/sdcard/Download','-type','f'],'download-files',20)
    exports=[p for p in text.splitlines() if p.endswith('.md')]
    export_pass=False
    for i,p in enumerate(exports):
        dest=out/('export-'+str(i)+'.md');cmd(['adb','pull',p,str(dest)],'pull-export-'+str(i),10)
        if ('# Visible frame text' in dest.read_text() and 'Mass and acceleration' in dest.read_text()) if focus else 'Mass and acceleration.' in dest.read_text():export_pass=True
    results.append({'check':'export-bytes','passed':export_pass})
except Exception as e:results.append({'check':'setup','passed':False,'error':str(e)})
finally:
    try:cmd(['adb','logcat','-d','-t','2000'],'final-logcat',15,False)
    except Exception as e:results.append({'check':'device-diagnostics','passed':False,'error':str(e)})
    logger.terminate();live.close()
    report={'scope':('Focused frame-study checks only; other release gates not included. ' if focus else '')+'KVM hosted API36 Google APIs emulator. Not physical-phone or lecture-domain accuracy qualification. CI-only disposable signing; no APK publication.','results':results,'all_covered_checks_passed':bool(results) and all(x['passed'] for x in results)}
    (out/'outcome.json').write_text(json.dumps(report,indent=2))
    print(json.dumps(report,indent=2),flush=True)
raise SystemExit(0 if report['all_covered_checks_passed'] else 1)
