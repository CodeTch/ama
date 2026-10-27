package ama.api;

import ama.contract.IdentityContracts.ResolvedSession;
import ama.contract.WorkspaceContracts.CategoryPage;
import ama.meeting.JdbcCategoryCatalog;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
class CategoryController {
 private final JdbcCategoryCatalog catalog;
 CategoryController(JdbcCategoryCatalog catalog){this.catalog=catalog;}
 @GetMapping("/api/v1/meetings/categories") ResponseEntity<CategoryPage> read(HttpServletRequest request) {
  request.getParameterMap().forEach((name,values)->{if(!Set.of("limit","cursor").contains(name)||values.length!=1)throw new IllegalArgumentException("Invalid query");});
  String raw=request.getParameter("limit");int limit=20;
  if(raw!=null){if(!raw.matches("[1-9][0-9]?"))throw new IllegalArgumentException("Invalid page limit");limit=Integer.parseInt(raw);}
  if(limit>50)throw new IllegalArgumentException("Invalid page limit");
  var actor=((ResolvedSession)request.getAttribute(SessionSecurityFilter.SESSION)).actor();
  return ResponseEntity.ok().header("Cache-Control","private, no-store").body(catalog.read(actor,limit,request.getParameter("cursor")));
 }
}
