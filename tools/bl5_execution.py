"""Validate the COMPLETE explicit Java execution inventory before reading any artifact bytes."""
import hashlib,pathlib,re
DEPENDENCIES=pathlib.Path('/home/tanren/.m2/repository')
def classpath_entries(raw,project):
 if not project.is_absolute():raise ValueError('Absolute owned build required')
 result=[]
 # Empty Java classpath entries mean cwd. Exclude them explicitly, never convert them to Path().
 for entry in raw.split(':'):
  if not entry.strip():continue
  path=pathlib.Path(entry)
  if entry!=entry.strip() or not path.is_absolute() or '..' in path.parts or '.' in path.parts:raise ValueError('Noncanonical execution entry')
  if path.is_relative_to(project):
   relative=str(path.relative_to(project))
   if not re.fullmatch(r'backend/[a-z-]+/target/(classes|test-classes|[A-Za-z0-9_.-]+\.jar)',relative):raise ValueError('Foreign build artifact entry')
  elif not path.is_relative_to(DEPENDENCIES) or path.suffix!='.jar':raise ValueError('Foreign dependency entry')
  if path not in result:result.append(path)
 if not result:raise ValueError('Empty execution closure')
 return result

def collect(raw,project):
 entries=classpath_entries(raw,project) # Validates ALL entries before file-system enumeration.
 if project.resolve()!=project:raise ValueError('Linked owned build ancestor')
 for path in entries:
  if path.resolve()!=path:raise ValueError('Linked execution ancestor')
 artifacts={};directories={};actual=[]
 for path in entries:
  if path.is_symlink():raise ValueError('Linked execution entry')
  if path.is_dir():
   files=sorted(x for x in path.rglob('*') if x.is_file() or x.is_symlink())
   if any(x.is_symlink() for x in files):raise ValueError('Linked runtime resource')
   directories[str(path)]=[str(x.relative_to(path)) for x in files]
   for file in files:artifacts[str(file)]=hashlib.sha256(file.read_bytes()).hexdigest()
  elif path.is_file():artifacts[str(path)]=hashlib.sha256(path.read_bytes()).hexdigest()
  elif path.is_relative_to(project) and path.name in ('classes','test-classes'):continue # Maven may supply absent empty output directories.
  else:raise ValueError('Missing execution artifact')
  actual.append(str(path))
 return ':'.join(actual),artifacts,directories
