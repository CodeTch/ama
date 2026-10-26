import pathlib,socket,sys,unittest
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
from knowledge_metadata_relay import read_header,HeaderReadFailure
class MetadataHeaderTests(unittest.TestCase):
 def test_only_empty_eof_is_pre_request_close(self):
  a,b=socket.socketpair()
  try:b.close();self.assertIsNone(read_header(a))
  finally:a.close()
 def test_partial_eof_remains_failure_with_exact_byte_count(self):
  a,b=socket.socketpair()
  try:
   b.sendall(b'GET /');b.close()
   with self.assertRaises(HeaderReadFailure) as raised:read_header(a)
   self.assertEqual(('HEADER_EOF_PARTIAL',5),(raised.exception.code,raised.exception.header_bytes))
  finally:a.close()
 def test_timeout_even_with_zero_bytes_is_failure(self):
  a,b=socket.socketpair()
  try:
   a.settimeout(.02)
   with self.assertRaises(HeaderReadFailure) as raised:read_header(a)
   self.assertEqual(('HEADER_TIMEOUT',0),(raised.exception.code,raised.exception.header_bytes))
  finally:a.close();b.close()
 def test_complete_header_returns_exact_bytes(self):
  a,b=socket.socketpair();raw=b'GET /path HTTP/1.1\r\nContent-Length: 0\r\n\r\n'
  try:b.sendall(raw);self.assertEqual(raw,read_header(a))
  finally:a.close();b.close()
if __name__=='__main__':unittest.main()
