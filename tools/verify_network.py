from pathlib import Path
import os,subprocess,socket,time,json
root=Path(__file__).resolve().parent.parent
java=os.environ.get('NETTY_BOOK_JAVA','java')
cp=str(root/'examples/target/classes')+os.pathsep+(root/'examples/target/classpath.txt').read_text().strip()
with socket.socket() as s:s.bind(('127.0.0.1',0));port=s.getsockname()[1]
log=(root/'research/server-run.log').open('w')
p=subprocess.Popen([java,'-cp',cp,'book.LineServer',str(port)],stdout=log,stderr=subprocess.STDOUT)
try:
 for i in range(100):
  if p.poll() is not None:raise RuntimeError('Server exited; inspect server-run.log')
  try:
   with socket.create_connection(('127.0.0.1',port),timeout=.2):break
  except OSError:time.sleep(.1)
 else:raise RuntimeError('Server did not start')
 r=subprocess.run([java,'-cp',cp,'book.LineClient',str(port)],capture_output=True,text=True,timeout=20)
 assert r.returncode==0,(r.stdout,r.stderr)
 assert r.stdout.splitlines()==['收到：你好，Netty','再见'],r.stdout
 with socket.create_connection(('127.0.0.1',port),timeout=3) as s:
  s.sendall(b'he');s.sendall(b'llo\nworld\nquit\n');f=s.makefile('rb')
  result=[f.readline().decode().rstrip('\n') for _ in range(3)]
  assert result==['收到：hello','收到：world','再见'],result
  assert f.read()==b''
 report={'java_client':r.stdout.splitlines(),'socket_client':result,'passed':True}
 (root/'research/network-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
 print(json.dumps(report,ensure_ascii=False))
finally:
 p.terminate()
 try:p.wait(timeout=10)
 except subprocess.TimeoutExpired:p.kill();p.wait()
 log.close()
