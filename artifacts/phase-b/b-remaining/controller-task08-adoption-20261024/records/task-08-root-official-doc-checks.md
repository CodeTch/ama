# Task8 primary documentation checks — 2026-10-23

Root browsed official MySQL8.4 manual for the niche exact-DDL premises before static review. These checks are documentary, not execution on pinned8.4.6.

- AUTO_INCREMENT may use an index beginning with that column; the proposed single-column unique index fits. https://dev.mysql.com/doc/refman/8.4/en/innodb-auto-increment-handling.html
- CHECK expressions cannot reference AUTO_INCREMENT columns. Positive/immutable metadata therefore needs the separately stated application/restore verification; the SQL adds no such CHECK. https://dev.mysql.com/doc/refman/8.4/en/create-table-check-constraints.html
- Adding an auto-increment column reorganizes data and does not allow concurrent DML; at least INPLACE/SHARED behavior is needed. Preserve quiesced owned-target physical/olddata/history/rerun gates and do not label the unexecuted ALTER instant/online. https://dev.mysql.com/doc/refman/8.4/en/innodb-online-ddl-operations.html

Exact unversioned candidate is164B SHA023fe875efd2be0bbd9d59b2d22a9b6429ea88eb6fc7a7b30d8541a53c0d46ee. No formalallocation/freeze/ACK/application here; actualengine/DDL/model/source/fairness gates remain later.
