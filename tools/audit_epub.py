from pathlib import Path
from zipfile import ZipFile,ZIP_STORED
import xml.etree.ElementTree as E
from urllib.parse import unquote,urlsplit
import posixpath,json
root=Path(__file__).resolve().parent.parent
with ZipFile(root/'dist/从零开始学Netty.epub') as z:
 names=set(z.namelist());assert z.infolist()[0].filename=='mimetype';assert z.infolist()[0].compress_type==ZIP_STORED
 assert z.read('mimetype')==b'application/epub+zip'
 docs={n:E.fromstring(z.read(n)) for n in names if n.endswith(('.xhtml','.xml','.opf','.ncx'))}
 for n,doc in docs.items():
  for el in doc.iter():
   for attr in ('href','src'):
    link=el.get(attr)
    if not link:continue
    u=urlsplit(link)
    if u.scheme or u.netloc:continue
    dest=posixpath.normpath(posixpath.join(posixpath.dirname(n),unquote(u.path))) if u.path else n
    assert dest in names,(n,link)
    if u.fragment and dest in docs:
     assert any(x.get('id')==unquote(u.fragment) for x in docs[dest].iter()),(n,link)
 alltext='\n'.join(''.join(d.itertext()) for n,d in docs.items() if '/text/' in n)
 for i in range(1,16):assert f'第 {i} 章' in alltext
 for p in (root/'examples').rglob('*'):
  if p.suffix not in ('.java','.xml') or 'target' in p.parts:continue
  assert p.read_text().strip() in alltext,str(p)
 report={'xml_documents':len(docs),'all_internal_links_valid':True,'chapters':15,'embedded_source_matches_project':True}
 (root/'research/epub-audit.json').write_text(json.dumps(report,indent=2));print(report)
