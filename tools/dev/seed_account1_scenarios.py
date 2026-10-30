import json, subprocess
from pathlib import Path
root=Path('/home/tanren/ama'); env=dict(x.split('=',1) for x in (root/'.tools/local-app/mysql.env').read_text().splitlines() if '=' in x); state=json.loads((root/'.tools/local-app/state.json').read_text()); c='ama-local-dev-'+state['owner']
base=['docker','exec','-i','-e','MYSQL_PWD='+env['MYSQL_PASSWORD'],c,'mysql','--default-character-set=utf8mb4','-u'+env['MYSQL_USER'],env['MYSQL_DATABASE']]
q=lambda x: "'"+x.replace("'","''")+"'"
# clear all business rows
names=subprocess.check_output(['docker','exec','-e','MYSQL_PWD='+env['MYSQL_PASSWORD'],c,'mysql','--batch','--skip-column-names','-u'+env['MYSQL_USER'],env['MYSQL_DATABASE'],'-e',"SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name!='flyway_schema_history' AND table_name!='task_outbox_sequence'"],text=True).splitlines()
s=['SET FOREIGN_KEY_CHECKS=0;']+[f'DELETE FROM `{n}`;' for n in names]+['SET FOREIGN_KEY_CHECKS=1;']
s.append("INSERT INTO task_outbox_sequence(id,next_id) VALUES (1,1) ON DUPLICATE KEY UPDATE id=id;")
ids=[('1','测试账号1','本地测试',0,'ACTIVE'),('dev-owner','开发负责人','本地开发',0,'ACTIVE'),('dev-member','开发参会人','本地开发',0,'ACTIVE'),('dev-viewer','开发访客','本地开发',0,'ACTIVE'),('dev-admin','开发平台管理员','本地开发',1,'ACTIVE'),('dev-revoked','已撤权用户','本地开发',0,'DISABLED')]
s.append('INSERT INTO enterprise_identity(enterprise_id,display_name,department,status,platform_admin,session_generation,verified_at) VALUES '+','.join('(%s,%s,%s,%s,%d,0,UTC_TIMESTAMP(6))'%(q(a),q(b),q(c),q(status),d) for a,b,c,d,status in ids)+';')
s.append('INSERT INTO role_assignment(enterprise_id,role,assigned_at) VALUES '+','.join('(%s,%s,UTC_TIMESTAMP(6))'%(q(a),q('PLATFORM_ADMIN')) for a,b,c,d,status in ids if d)+' ON DUPLICATE KEY UPDATE assigned_at=VALUES(assigned_at);')
stages=['ENTRY','TRANSCRIPT_PENDING','TRANSCRIPT_FAILED','MINUTES_PENDING','MINUTES_FAILED','READY_TO_PUBLISH','PUBLISH_FAILED','PUBLISHED']; life=['ACTIVE','DELETING','DELETED']; gov=['ACTIVE','WITHDRAW_PENDING','WITHDRAWN','OBSOLETE_PENDING','OBSOLETE']; cats=['project','review',None,'planning','decision','training']
for i in range(1,31):
    mid=f'{i:08d}-0000-4000-8000-{i:012d}'; info=f'{i:08d}-1000-4000-8000-{i:012d}'; roster=f'{i:08d}-2000-4000-8000-{i:012d}'
    d='2026-10-%02d'%((i%28)+1) if i%7 else 'NULL'; dsql='NULL' if d=='NULL' else q(d); cat=cats[(i-1)%len(cats)]; catv='NULL' if cat is None else q(cat)
    confirmed=i%3!=0; state='CONFIRMED' if confirmed else 'DRAFT'; conf="UTC_TIMESTAMP(6)" if confirmed else 'NULL'; by=q('1') if confirmed else 'NULL'; stage=stages[(i-1)%8]; lc=life[(i-1)%3]; gv=gov[(i-1)%5]
    s += [f"INSERT INTO meeting(id,owner_id,revision,access_revision,stage,lifecycle,governance_state,ever_published,created_at,updated_at) VALUES ({q(mid)},'1',1,{1 if confirmed else 0},{q(stage)},{q(lc)},{q(gv)},{1 if stage=='PUBLISHED' else 0},UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));",f"INSERT INTO meeting_minutes_editor_guard(meeting_id,intent_epoch) VALUES ({q(mid)},0);",f"INSERT INTO meeting_info_revision(id,meeting_id,revision,name,category_id,summary,actual_meeting_date,created_by,created_at) VALUES ({q(info)},{q(mid)},0,{q('账号1场景会议 %02d'%i)},{catv},{q('账号1多类型多状态测试场景 %02d'%i)},{dsql},'1',UTC_TIMESTAMP(6));",f"INSERT INTO roster_revision(id,meeting_id,revision,state,confirmed_at,confirmed_by) VALUES ({q(roster)},{q(mid)},0,{q(state)},{conf},{by});",f"UPDATE meeting SET info_revision_id={q(info)},roster_revision_id={q(roster)},actual_meeting_date={dsql} WHERE id={q(mid)};",f"INSERT INTO meeting_search_projection(meeting_id,info_revision_id,roster_revision_id,name_search,number_search,category_id,meeting_revision) VALUES ({q(mid)},{q(info)},{q(roster)},{q('账号1场景会议 %02d'%i)},{q(mid)},{catv},0);"]
    people=[] if i%5==0 else (['dev-owner','dev-member']+(['dev-viewer'] if i%2==0 else []))
    if confirmed:
      for j,p in enumerate(people):
        member=f'{i:08d}-3000-4000-8000-{j+1:012d}'; s.append(f"INSERT INTO roster_member(roster_id,member_id,enterprise_id,display_name,member_kind) VALUES ({q(roster)},{q(member)},{q(p)},{q({'dev-owner':'开发负责人','dev-member':'开发参会人','dev-viewer':'开发访客'}[p])},'ENTERPRISE');")
        s.append(f"INSERT INTO meeting_access_grant(meeting_id,enterprise_id,action,roster_id,confirmed_at) VALUES ({q(mid)},{q(p)},'READ',{q(roster)},UTC_TIMESTAMP(6));")
      if i%4==0:
        member=f'{i:08d}-3000-4000-8000-000000000099'; s.append(f"INSERT INTO roster_member(roster_id,member_id,enterprise_id,display_name,member_kind) VALUES ({q(roster)},{q(member)},NULL,{q('外部参会人%02d'%i)},'EXTERNAL');")
    
sql='\n'.join(s)+'\n'
subprocess.run(base,input=sql,text=True,check=True)
print('seeded',len(s))
