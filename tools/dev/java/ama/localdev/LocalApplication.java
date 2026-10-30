package ama.localdev;

import ama.api.ApiApplication;
import ama.contract.IdentityContracts.*;
import ama.contract.MeetingContracts.SaveInfo;
import ama.contract.MeetingContracts.ConfirmRoster;
import ama.identity.DirectoryPort;
import ama.meeting.*;
import ama.db.MysqlDataSources;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

/** Explicit development classpath only. Never packaged in the production API artifact. */
@Configuration(proxyBeanMethods=false)
@Import(LocalLogin.class)
public class LocalApplication {
    static final Map<String,String> USERS=Map.of("1","测试账号1","dev-owner","开发负责人","dev-member","开发参会人","dev-viewer","开发访客","dev-admin","开发平台管理员");

    public static void main(String[] args) {
        requireLocalConfiguration();
        new SpringApplicationBuilder(ApiApplication.class,LocalApplication.class).run(args);
    }

    private static void requireLocalConfiguration() {
        if (!"1".equals(System.getenv("AMA_LOCAL_DEV"))) throw new IllegalStateException("Explicit local development opt-in required");
        String url=System.getenv("DATABASE_URL");
        if(url==null||!url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/ama_local_dev\\?.+"))
            throw new IllegalStateException("An isolated loopback ama_local_dev database is required");
    }

    @Bean DataSource localDevelopmentDataSource() {
        requireLocalConfiguration();
        // Supply the actual DataSource, so spring.datasource.* and external
        // Spring configuration cannot redirect the API away from migration's
        // exact, launcher-generated DATABASE_* connection parameters.
        var pool=new HikariDataSource();
        pool.setDataSource(MysqlDataSources.fromEnvironment("DATABASE_"));
        pool.setMaximumPoolSize(10);pool.setMinimumIdle(0);
        pool.setInitializationFailTimeout(-1);pool.setConnectionTimeout(1000);
        return pool;
    }

    @Bean @Primary DirectoryPort localDirectory(DataSource source) {
        var jdbc=new JdbcTemplate(source);
        return (query,cursor,limit)-> {
            if(limit<1||limit>100||query==null||query.length()>255||(cursor!=null&&!USERS.containsKey(cursor)))
                throw new IllegalArgumentException("Invalid development directory query");
            var rows=jdbc.query("""
                SELECT enterprise_id,display_name FROM enterprise_identity
                WHERE status='ACTIVE' AND enterprise_id IN ('1','dev-owner','dev-member','dev-viewer','dev-admin')
                AND (LOCATE(?,enterprise_id)>0 OR LOCATE(?,display_name)>0) AND enterprise_id>?
                ORDER BY enterprise_id LIMIT ?
                """,(r,n)->new Person(r.getString(1),r.getString(2),"本地开发",true),query,query,cursor==null?"":cursor,limit+1);
            boolean more=rows.size()>limit;
            var page=List.copyOf(rows.subList(0,Math.min(rows.size(),limit)));
            return new PeoplePage(page,more?page.getLast().stableId():null);
        };
    }

    @Bean @Primary CategoryDictionary localCategories(Environment env)throws Exception {
        return CategoryDictionary.load(Path.of(env.getRequiredProperty("local-dev.categories")),
            env.getRequiredProperty("local-dev.categories-sha"),new WorkspaceReadBudget());
    }

    @Bean ApplicationRunner localSeed(DataSource source,MeetingService meetings,RosterService rosters,Environment env) {
        return args->{
            Path marker=Path.of(env.getRequiredProperty("local-dev.seed-marker"));
            var jdbc=new JdbcTemplate(source);
            Path adminMarker=marker.resolveSibling("seeded-admin");
            if(!Files.exists(adminMarker)){
                new ama.db.TransactionRunner(source).required(()->{
                    jdbc.update("INSERT IGNORE INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES ('dev-admin',?,'ACTIVE',UTC_TIMESTAMP(6))",USERS.get("dev-admin"));
                    var roles=jdbc.queryForList("SELECT role FROM role_assignment WHERE enterprise_id='dev-admin'",String.class);
                    if(roles.stream().anyMatch(role->!role.equals("PLATFORM_ADMIN")))throw new IllegalStateException("Local admin fixture already has another role");
                    jdbc.update("INSERT IGNORE INTO role_assignment(enterprise_id,role,assigned_at) VALUES ('dev-admin','PLATFORM_ADMIN',UTC_TIMESTAMP(6))");
                    return null;
                });
                Files.writeString(adminMarker,"Local platform-admin-only fixture initialized\n",StandardOpenOption.CREATE_NEW);
            }
            for(var user:USERS.entrySet()) {
                jdbc.update("INSERT IGNORE INTO enterprise_identity(enterprise_id,display_name,status,verified_at) VALUES (?,?,'ACTIVE',UTC_TIMESTAMP(6))",user.getKey(),user.getValue());
                jdbc.update("INSERT IGNORE INTO role_assignment(enterprise_id,role,assigned_at) VALUES (?,?,UTC_TIMESTAMP(6))",user.getKey(),user.getKey().equals("dev-admin")?"PLATFORM_ADMIN":"USER");
            }
            if(Files.exists(marker))return;
            var day=LocalDate.parse(env.getRequiredProperty("local-dev.seed-date"));
            seed(meetings,rosters,"dev-owner","dev-member","产品周会（本地示例）","project",day);
            seed(meetings,rosters,"dev-member","dev-owner","需求评审（本地示例）","review",day);
            Files.writeString(marker,"Seeded local development identities and meetings\n",StandardOpenOption.CREATE_NEW);
        };
    }

    private static void seed(MeetingService meetings,RosterService rosters,String owner,String member,String title,String category,LocalDate date) {
        var actor=new Actor(owner,Status.ACTIVE,Set.of(ama.contract.IdentityContracts.Role.USER));
        String key="local-demo-v1-"+owner;
        var meeting=meetings.create(actor,key+"-create",UUID.randomUUID().toString());
        var saved=meetings.save(actor,meeting.id(),key+"-info",new SaveInfo(0L,title,category,
            "本地开发示例会议，用于验证登录、会议列表、详情和人员权限。",date,List.of(member),List.of()),UUID.randomUUID().toString());
        rosters.confirm(actor,meeting.id(),key+"-roster",new ConfirmRoster(saved.revision()),UUID.randomUUID().toString());
    }
}
