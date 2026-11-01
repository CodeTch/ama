-- Material snapshot changes previously advanced the root/fence without its unchanged search row.
-- Reconcile only rows whose complete search metadata still equals the authoritative current info/roster.
-- Missing rows, differing searchable values, forward versions or mismatched fences remain unavailable.
UPDATE meeting_search_projection p
JOIN meeting m ON m.id=p.meeting_id
JOIN meeting_info_revision i ON i.id=m.info_revision_id AND i.meeting_id=m.id
JOIN task_subject f ON f.subject_kind='MEETING' AND f.subject_id=m.id
SET p.meeting_revision=m.revision
WHERE p.meeting_revision<m.revision AND p.info_revision_id=m.info_revision_id
AND (p.roster_revision_id <=> m.roster_revision_id)
AND (BINARY p.name_search <=> BINARY i.name) AND (BINARY p.category_id <=> BINARY i.category_id)
AND BINARY p.number_search=BINARY m.id AND (i.actual_meeting_date <=> m.actual_meeting_date)
AND f.business_version=m.revision AND f.cancel_epoch=m.cancel_epoch AND f.deleted=0;
