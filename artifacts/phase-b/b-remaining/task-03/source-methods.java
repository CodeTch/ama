    private enum SourcePhase { SOURCES_HELD, BINDING, CONSUMED }
    private final class Sources implements LockedSources {
        final Gate gate;
        final SortedMap<String,Original> originals;
        final SortedMap<String,FileRecord> files;
        SourcePhase phase=SourcePhase.SOURCES_HELD;
        private Sources(Gate gate,SortedMap<String,Original> originals,SortedMap<String,FileRecord> files){
            this.gate=gate;this.originals=Collections.unmodifiableSortedMap(new TreeMap<>(originals));
            this.files=Collections.unmodifiableSortedMap(new TreeMap<>(files));
        }
        JdbcFileRegistry issuer(){return JdbcFileRegistry.this;}
    }
    private final class SourceAcquisition {
        long rows,bytes,scalars,edges;
        void check(){if(Thread.currentThread().isInterrupted())throw new Unavailable();}
        void row(long edgeCount){check();if(++rows>sourceBudget.maxFactRows()||edgeCount>sourceBudget.maxFactEdges()-edges)throw new Unavailable();edges+=edgeCount;}
        void text(long octets,long points,long units){
            check();if(octets<0||points<0||units<0||octets>sourceBudget.maxStringUtf8Bytes()
                    ||units>sourceBudget.maxStringUtf16()||octets>sourceBudget.maxTotalBytes()-bytes
                    ||points>sourceBudget.maxAggregateScalars()-scalars)throw new Unavailable();
            bytes+=octets;scalars+=points;
        }
    }
    private SortedSet<String> completeIds(List<String> ids){
        if(sourceBudget==null)throw new Unavailable();
        if(ids==null||ids.isEmpty()||ids.size()>sourceBudget.maxBatchFiles())throw new IllegalArgumentException("Complete bounded source set required");
        var result=new TreeSet<String>();for(String id:ids){uuid(id);if(!result.add(id))throw new IllegalArgumentException("Duplicate source");}return result;
    }
    @Override public Optional<LockedSources> prelockSources(LockedMeeting held,List<String> ids,boolean skip){
        var gate=gate(held);var complete=completeIds(ids);
        if(gate.filesStarted)throw new IllegalStateException("Source acquisition already started");
        gate.filesStarted=true;
        var state=new SourceAcquisition();var associations=new TreeMap<String,String>();
        for(String id:complete){state.check();var found=originalIds(id);if(found.size()!=1)throw new NotFound();associations.put(id,found.getFirst());}
        var originals=new TreeMap<String,Original>();
        for(String upload:new TreeSet<>(associations.values())){
            if(!measure("upload_session",upload,List.of("id","meeting_id","purpose","backend_id","namespace","object_key","expected_sha256"),skip,state,3))return Optional.empty();
            var rows=j.query("SELECT id,meeting_id,purpose,cancel_epoch,backend_id,namespace,object_key,expected_size,expected_sha256,state,completed_file_id FROM upload_session WHERE id=?",
                (r,n)->new Object[]{new Original(r.getString(1),r.getString(2),r.getString(3),exact(r,"cancel_epoch"),new StorageKey(r.getString(5),r.getString(6),r.getString(7)),exact(r,"expected_size"),r.getString(9)),r.getString(10),r.getString(11)},upload);
            if(rows.size()!=1)throw new NotFound();var row=rows.getFirst();var original=(Original)row[0];
            String file=(String)row[2];
            if(!"COMPLETED".equals(row[1])||!upload.equals(associations.get(file))||!gate.meeting.equals(original.meeting())||original.hash()==null)throw new NotFound();
            originals.put(file,original);
        }
        // Recheck complete associations after all original primary-key locks, never LIMIT 1.
        for(String id:complete)if(!originalIds(id).equals(List.of(associations.get(id))))throw new Conflict();
        var files=new TreeMap<String,FileRecord>();
        for(String id:complete){
            if(!measure("file_object",id,List.of("id","meeting_id","backend_id","storage_type","namespace","object_key","sha256","state"),skip,state,2))return Optional.empty();
            var rows=j.query("SELECT id,meeting_id,backend_id,namespace,object_key,size_bytes,sha256,state,location_revision,reference_epoch,storage_type FROM file_object WHERE id=?",
                (r,n)->{
                    var backend=backends.require(r.getString(3),false);
                    if(!backend.type().equals(r.getString(11)))throw new Unavailable();
                    return new FileRecord(r.getString(1),r.getString(2),new StorageKey(r.getString(3),r.getString(4),r.getString(5)),exact(r,"size_bytes"),r.getString(7),r.getString(8),exact(r,"location_revision"),exact(r,"reference_epoch"),null);
                },id);
            if(rows.size()!=1)throw new NotFound();var file=rows.getFirst();checkOriginal(gate,originals.get(id),file);
            if(file.referenceEpoch()>=Long.MAX_VALUE-1||file.locationRevision()>=Long.MAX_VALUE-1)throw new Conflict();files.put(id,file);
        }
        return Optional.of(new Sources(gate,originals,files));
    }
    private List<String> originalIds(String file){
        return j.queryForList("SELECT id FROM upload_session WHERE completed_file_id=? AND state='COMPLETED' ORDER BY id LIMIT 2",String.class,file);
    }
    private boolean measure(String table,String id,List<String> columns,boolean skip,SourceAcquisition state,long edges){
        String projection=String.join(",",columns.stream().map(c->"COALESCE(OCTET_LENGTH("+c+"),0),COALESCE(CHAR_LENGTH("+c+"),0),COALESCE(OCTET_LENGTH(CONVERT("+c+" USING utf16)) DIV 2,0)").toList());
        return j.query("SELECT "+projection+" FROM "+table+" WHERE id=? FOR UPDATE"+(skip?" SKIP LOCKED":""),rs->{
            state.check();if(!rs.next()){if(skip)return false;throw new NotFound();}state.row(edges);
            for(int i=0;i<columns.size();i++)state.text(rs.getLong(i*3+1),rs.getLong(i*3+2),rs.getLong(i*3+3));return true;
        },id);
    }
    private static long exact(java.sql.ResultSet rs,String column)throws java.sql.SQLException{
        try{return rs.getBigDecimal(column).longValueExact();}catch(ArithmeticException|NullPointerException failure){throw new Conflict();}
    }
    private Sources checkedSources(LockedMeeting expected,LockedSources held,List<String> ids){
        var gate=gate(expected);var complete=completeIds(ids);
        if(!(held instanceof JdbcFileRegistry.Sources sources)||sources.issuer()!=this||sources.gate!=gate
                ||sources.phase!=SourcePhase.SOURCES_HELD||!sources.files.keySet().equals(complete))throw new IllegalArgumentException("Foreign, consumed or incomplete sources");
        gate(sources.gate);return sources;
    }
    @Override public List<CompletedSource> sources(LockedMeeting expected,LockedSources held,List<String> ids){
        var sources=checkedSources(expected,held,ids);
        return sources.files.entrySet().stream().map(e->{var o=sources.originals.get(e.getKey());return new CompletedSource(e.getValue(),o.id(),o.purpose(),o.epoch());}).toList();
    }
