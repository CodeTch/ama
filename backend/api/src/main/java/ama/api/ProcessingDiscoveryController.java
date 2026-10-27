package ama.api;
import ama.contract.IdentityContracts.ResolvedSession;
import ama.contract.ProcessingContracts.Page;
import ama.transcript.JdbcProcessingDiscovery;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/meetings/{id}") class ProcessingDiscoveryController {
    private final JdbcProcessingDiscovery discovery;
    ProcessingDiscoveryController(JdbcProcessingDiscovery discovery) {
        this.discovery=discovery;
    }
    @GetMapping("/processing-capabilities") ResponseEntity<?> capabilities(@PathVariable String id,HttpServletRequest r) {
        query(r,Set.of());
        return response(discovery.capabilities(actor(r),id));
    }
    @GetMapping("/uploads") ResponseEntity<Page> uploads(@PathVariable String id,HttpServletRequest r) {
        query(r,Set.of("limit","after"));
        return response(discovery.uploads(actor(r),id,limit(r,20),r.getParameter("after")));
    }
    @GetMapping("/uploads/{upload}") ResponseEntity<?> upload(@PathVariable String id,@PathVariable String upload,HttpServletRequest r) {
        query(r,Set.of());
        return response(discovery.upload(actor(r),id,upload));
    }
    @GetMapping("/uploads/{upload}/parts") ResponseEntity<Page> parts(@PathVariable String id,@PathVariable String upload,HttpServletRequest r) {
        query(r,Set.of("limit","afterPart"));
        return response(discovery.parts(actor(r),id,upload,limit(r,100),decimal(r.getParameter("afterPart"),0)));
    }
    @GetMapping("/completed-files") ResponseEntity<Page> files(@PathVariable String id,HttpServletRequest r) {
        query(r,Set.of("limit","after"));
        return response(discovery.completedFiles(actor(r),id,limit(r,20),r.getParameter("after")));
    }
    @GetMapping("/processing-inputs") ResponseEntity<Page> inputs(@PathVariable String id,HttpServletRequest r) {
        query(r,Set.of("limit","after"));
        return response(discovery.inputs(actor(r),id,limit(r,20),r.getParameter("after")));
    }
    @GetMapping("/processing-inputs/{input}") ResponseEntity<?> input(@PathVariable String id,@PathVariable String input,HttpServletRequest r) {
        query(r,Set.of());
        return response(discovery.input(actor(r),id,input));
    }
    @GetMapping("/processing-tasks") ResponseEntity<Page> tasks(@PathVariable String id,HttpServletRequest r) {
        query(r,Set.of("limit","after","scope"));
        return response(discovery.tasks(actor(r),id,r.getParameter("scope")==null?"active":r.getParameter("scope"),limit(r,20),r.getParameter("after")));
    }
    @GetMapping("/processing-tasks/{task}") ResponseEntity<?> task(@PathVariable String id,@PathVariable String task,HttpServletRequest r) {
        query(r,Set.of());
        return response(discovery.task(actor(r),id,task));
    }
    private static ama.contract.IdentityContracts.Actor actor(HttpServletRequest r) {
        return ((ResolvedSession)r.getAttribute(SessionSecurityFilter.SESSION)).actor();
    }
    private static void query(HttpServletRequest r,Set<String> allowed) {
        r.getParameterMap().forEach((key,values)-> {
            if(!allowed.contains(key)||values.length!=1)throw new IllegalArgumentException("Invalid query");
        });
    }
    private static int limit(HttpServletRequest r,int fallback) {
        long value=decimal(r.getParameter("limit"),fallback);
        if(value>Integer.MAX_VALUE)throw new IllegalArgumentException("Invalid page limit");
        return (int)value;
    }
    private static long decimal(String value,long fallback) {
        if(value==null)return fallback;
        if(value.isEmpty()||value.length()>19||value.length()>1&&value.charAt(0)=='0')throw new IllegalArgumentException("Invalid cursor");
        for(char c:value.toCharArray())if(c<'0'||c>'9')throw new IllegalArgumentException("Invalid cursor");
        try {
            return Long.parseLong(value);
        }
        catch(NumberFormatException failure) {
            throw new IllegalArgumentException("Invalid cursor");
        }
    }
    private static <T>ResponseEntity<T> response(T value) {
        return ResponseEntity.ok().header("Cache-Control","private, no-store").body(value);
    }
}
