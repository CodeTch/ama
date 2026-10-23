from pathlib import Path
import subprocess,json,hashlib
root=Path.cwd();e=root/'artifacts/phase-b/b-remaining/task-04'
def git(*args):return subprocess.check_output(['git',*args],text=True).splitlines()
owned=sorted(set(git('diff','--name-only')+git('ls-files','--others','--exclude-standard','--','backend','contracts/openapi')))
assert owned and all(p.startswith(('backend/','contracts/openapi/')) for p in owned),owned
(e/'owned-paths.txt').write_text('\n'.join(owned)+'\n')
def lex(s):
 out=[];i=0
 while i<len(s):
  if s[i].isspace():i+=1;continue
  if s.startswith('//',i):
   end=s.find('\n',i);end=len(s) if end<0 else end;out.append(s[i:end]);i=end;continue
  if s.startswith('/*',i):end=s.index('*/',i)+2;out.append(s[i:end]);i=end;continue
  if s[i] in '\"\'':
   end=i+1
   while end<len(s):
    if s[end]=='\\':end+=2;continue
    if s[end]==s[i]:end+=1;break
    end+=1
   out.append(s[i:end]);i=end;continue
  out.append(s[i]);i+=1
 return out
def pretty(s):
 lines=[];buf='';depth=0;paren=0;brace=[];i=0
 def flush():
  nonlocal buf
  value=buf.strip()
  if value:lines.append('    '*depth+value)
  buf=''
 while i<len(s):
  c=s[i]
  if s.startswith('//',i):
   end=s.find('\n',i);end=len(s) if end<0 else end;buf+=s[i:end];flush();i=end;continue
  if s.startswith('/*',i):
   end=s.index('*/',i)+2;buf+=s[i:end];i=end
   if '\n' in buf or buf.startswith('/**'):flush()
   continue
  if c in '\"\'':
   end=i+1
   while end<len(s):
    if s[end]=='\\':end+=2;continue
    if s[end]==c:end+=1;break
    end+=1
   buf+=s[i:end];i=end;continue
  if c.isspace():
   if buf and not buf.endswith(' '):buf+=' '
   i+=1;continue
  if c=='(':paren+=1
  elif c==')':paren-=1
  if c=='{':
   buf=buf.rstrip()+' {';flush();brace.append(paren);depth+=1
  elif c=='}':
   flush();depth-=1;brace.pop();buf='}'
   rest=s[i+1:].lstrip()
   if not rest.startswith((')', ';', ',')):flush()
  elif c==';':
   buf+=c
   if paren==0 or brace and paren==brace[-1]:flush()
  else:buf+=c
  i+=1
 flush();return '\n'.join(lines)+'\n'
receipt={}
for name in owned:
 p=root/name;old=p.read_bytes();s=p.read_text()
 new_file=subprocess.run(['git','cat-file','-e','HEAD:'+name],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode!=0
 result=pretty(s) if name.endswith('.java') and new_file else '\n'.join(line.rstrip() for line in s.splitlines()).rstrip()+'\n'
 if name.endswith('.java'):assert lex(s)==lex(result),name
 p.write_text(result,newline='\n')
 receipt[name]={'before_sha256':hashlib.sha256(old).hexdigest(),'after_sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'new_file_readability_format':new_file and name.endswith('.java'),'java_token_stream_unchanged':name.endswith('.java')}
(e/'source-normalization.json').write_text(json.dumps(receipt,indent=2)+'\n')
print('owned_paths',len(owned),'java',sum(p.endswith('.java') for p in owned))
