# Task 09 initial mechanical custody — frozen historical windows

Frozen at `2026-10-24T10:31:00.592553+00:00`; indexed 165 pre-existing result windows. This is a read-only historical index, not a product or SPEC verdict. No test, build, SQL, Docker, service, migration, or network action was run.

## Protected adopted inputs

Compared raw bytes, sizes, and SHA-256 for all 20557 distinct paths from the 20557-entry protected manifest. Issues: **0**. No private file contents or individual secret digests are recorded.

## Window accounting

Window kinds: `{'old-collector': 28, 'new-collector': 134, 'non-test': 3}`. Qualifications: `{'qualified-suite-level': 13, 'assertion-failure': 52, 'pretest-compile-failure': 14, 'excluded': 3, 'qualified-interval': 60, 'test-error': 20, 'non-test-evidence': 3}`. Copied XML: 208; case observations: 315; distinct class#method keys: 127; keys with a qualified PASS: 127. The old `template-red-01` also copied 256 incidental unit observations across 256 separate keys. They remain indexed but are not selected Task09 case qualifications. Repeated windows do not add new keys.

Each window's command/result file hash, source manifest hash and snapshot-entry verification, XML counts/case identities, and report-custody copy/hash/closed-interval/before record are indexed in [task-09-mechanical-custody-initial-20261024.json](task-09-mechanical-custody-initial-20261024.json). Mechanical issue windows: 0; source snapshot issue windows: 0. The old collector's 28-window/69-XML suite-level audit is reused, not rerun. Its 68 matching XML and one stale XML remain subject to the recorded limit: no historical per-case before/closed interval proof.

## Required exclusions and limits

- `graph-controls-green-01`: all stale six copied cases excluded; zero canonical tests executed. Its copied failure count remains indexed as historical artifact, not business assertion.
- `application-provider-restart-02`: child 512 MiB makes the run ineligible. `application-provider-restart-03` is the 64 MiB qualification.
- `minutes-response-budget-green-02`: XML PASS retained, but the 503 expectation is R1-ineligible. `green-03` has the corrected 413 expectation.
- The sealed test snapshots confirm response-budget green-02 contains 503 and not 413, while green-03 contains 413 and not 503; restart-02 contains the 512 MiB child flag while restart-03 contains the 64 MiB child flag. The exact checks are recorded in JSON.
- Compile/setup or command failures with zero fresh XML are separate from assertion FAIL and test ERROR; all observed failure/error statuses remain in the index.
- Per-window source hashes are historical versions. Current source is not substituted. The before-image and interval fields corroborate new collector copies, but this audit cannot claim continuous per-case custody.

## Source-window disposition requiring author explanation

0 distinct case keys have no qualified PASS in the fixed historical set or have a later FAIL/ERROR observation. This is a mechanical follow-up list, not a defect count; some are intentional RED or maintenance windows. Exact keys and last qualified windows are in the JSON matrix.


## Checks requiring interpretation

Recorded before-image links matched the immediately preceding fixed new-collector Task09 report 69 times; 59 had no prior fixed new-collector report; 11 recorded no before image despite an earlier fixed report on that path (consistent with a prior deletion). 0 non-null before images differed from the last fixed Task09 report. The stored before image is not independently witnessed after the fact.

Final author source/raw freeze and secret/PEM scan are pending the coordinator's exact range and safe in-memory inputs. This initial index is intentionally limited to the fixed historical windows.

## Fixed window ledger

Counts below are selected result counts. Incidental implicit units are listed separately in the JSON.

| Window | Kind | Exit | T/F/E/S | XML | Source manifest SHA-256 | Qualification |
| --- | --- | ---: | ---: | ---: | --- | --- |
| `external-diagnostic-01` | non-test | 0 | –/–/–/– | 0 | `none` | non-test-evidence |
| `minutes-openapi-01` | non-test | – | –/–/–/– | 0 | `none` | non-test-evidence |
| `minutes-openapi-02` | non-test | – | –/–/–/– | 0 | `32772f37ef4a91291f236821370a20bf722064af123ecbfa7bb3fae66619468a` | non-test-evidence |
| `bootstrap-01` | old-collector | 0 | 1/0/0/0 | 1 | `3d857ab7cf309a94bd360ee99cee59b6cda7f515df5412964c36476a3bb7f5f5` | qualified-suite-level |
| `bindings-red-01` | old-collector | 1 | 2/2/0/0 | 1 | `f7095ba5f2555559c4b9cfcc3e86f06ee0886d5389dee517fd689be3988a6c6c` | assertion-failure |
| `bindings-green-01` | old-collector | 0 | 2/0/0/0 | 1 | `156b9073d986dffb956d728602f38e19c232e2e0b612ad9f8dbc624f86171fec` | qualified-suite-level |
| `template-red-01` | old-collector | 1 | 3/3/0/0 | 43 | `0f64aed81588768bb84c196ad869afc6e6a7dc562b688db437b9976e5f7be082` | assertion-failure |
| `template-green-01` | old-collector | 0 | 3/0/0/0 | 1 | `20e9890e6683ef67bcbc254d8c6bd80acc621d51fea9f813b5423e2504f1c471` | qualified-suite-level |
| `provider-store-red-01` | old-collector | 1 | 8/8/0/0 | 1 | `d77691d5aae6934bb857d772f35b5c20fa1621f49d8e4b080fd6e4daa88d0759` | assertion-failure |
| `provider-store-green-01` | old-collector | 0 | 8/0/0/0 | 1 | `5c6ca6403aca056d0d725720831c6a3fc921d0e6024aa367de89fbdc909d991d` | qualified-suite-level |
| `anchors-red-01` | old-collector | 1 | 3/3/0/0 | 1 | `c9a519476c681fecb65ba282696c450b179489954348e123ffc49cf939fa93a8` | assertion-failure |
| `anchors-green-01` | old-collector | 0 | 3/0/0/0 | 1 | `228bd8723bbe0055223c213fba0ce25af03cd983403e6eb34f3c6477a0152a2f` | qualified-suite-level |
| `graph-discovery-red-01` | old-collector | 1 | 4/4/0/0 | 1 | `e98a0971278870dc9d99ecda170577f9036fdb80aa555b2d75c9619857663649` | assertion-failure |
| `graph-discovery-green-01` | old-collector | 0 | 4/0/0/0 | 1 | `245c5d93a19a9acd7ff8c16e65fadef78fb4d0c4be35b1723dc202178ee72d39` | qualified-suite-level |
| `protocol-red-01` | old-collector | 1 | 4/4/0/0 | 1 | `5daf162f0f40bfee3bfba5f9aa38bc9ab018b13cbc36166fcc347f6ad040fcdc` | assertion-failure |
| `protocol-green-01` | old-collector | 1 | 0/0/0/0 | 0 | `70cd60db58a0123b68ea7e9399b3d2aed65715cb3a7909afeee3101146c9d015` | pretest-compile-failure |
| `protocol-green-02` | old-collector | 0 | 4/0/0/0 | 1 | `5d8609b0f82b4acb96489ac24c1e1128829830947fb8811e949881923c7e31bf` | qualified-suite-level |
| `envelope-store-red-01` | old-collector | 1 | 4/4/0/0 | 1 | `3c52e5490d948241daa330633803372a2e18a51466864de776de9df65afbea09` | assertion-failure |
| `envelope-store-green-01` | old-collector | 0 | 4/0/0/0 | 1 | `fac0686887c060f4509f3631e6e20c4acc6dc484fb8de96c72e7c294842ad1e2` | qualified-suite-level |
| `provider-http-red-01` | old-collector | 1 | 5/5/0/0 | 1 | `e985b16e38a2cf10ffb9c533250b5f9c3a4c3f43a760fe3c7af082820fa2c5ec` | assertion-failure |
| `provider-http-green-01` | old-collector | 0 | 5/0/0/0 | 1 | `5916d6e97388afe048174619e6cba8ff85b2fa05409215d495040bad83b07f51` | qualified-suite-level |
| `provider-http-boundaries-01` | old-collector | 0 | 6/0/0/0 | 1 | `6535e951b228db7106da3828f7323e8a8ce285dd86c265087db786a82d137609` | qualified-suite-level |
| `transport-abort-red-01` | old-collector | 1 | 3/3/0/0 | 1 | `b3071cdbff0d8610bba3ae4b62af5963adf90c5242f9a23dca31735f9df1f01a` | assertion-failure |
| `transport-abort-green-01` | old-collector | 0 | 3/0/0/0 | 1 | `1fc1c9a5ed78db0d321b242a403b49ef7d12b1ce4c867d6ddb1b544656ae9103` | qualified-suite-level |
| `graph-capture-red-01` | old-collector | 1 | 0/0/0/0 | 0 | `44c58ad1b5f000eca1c359d4d70e23a579bbb9decae86adaa6ac49271c6b26f8` | pretest-compile-failure |
| `graph-capture-red-02` | old-collector | 1 | 4/4/0/0 | 1 | `06b436f19cb5b67b63ffb05259a6c80006ae0af64adfb2fc25478dd4054a61e4` | assertion-failure |
| `graph-capture-green-01` | old-collector | 1 | 4/1/0/0 | 1 | `2cf3812dd9e4ae2bf191cf7915156d3e3adae737114024987f96ef0ce9d6c8d2` | assertion-failure |
| `graph-capture-green-02` | old-collector | 0 | 4/0/0/0 | 1 | `10fdebf7d0dff67a751398f002e05c740dc4ef453579d28c96ba4247dee6b4af` | qualified-suite-level |
| `graph-controls-red-01` | old-collector | 1 | 6/2/0/0 | 1 | `4912cb6bae4884b6ef7420ee4398635ebbca8fc93c8739cdbf717403b7b34f2f` | assertion-failure |
| `graph-controls-green-01` | old-collector | 1 | 6/2/0/0 | 1 | `2c361d145f77128ce4b54e6bb079516d15a99a0ce2a24c075b8bb70aa231f0e1` | excluded |
| `graph-controls-green-02` | old-collector | 0 | 10/0/0/0 | 2 | `07e1f0c3a2fb7ad1365f2a8ff6bde4629dfa6dd97cfda936d1346e6ed6d4e955` | qualified-suite-level |
| `graph-sources-red-01` | new-collector | 1 | 8/0/2/0 | 1 | `40fa7a43105eeac09c32d0c56eda427f9a7f3d6d6e7ff70392b87863c20b9a35` | test-error |
| `graph-sources-red-02` | new-collector | 1 | 8/2/0/0 | 1 | `ceae9f08fcaf335169284ff3556021b26a31257b18bd68ad401ff02b9cd7d883` | assertion-failure |
| `graph-sources-green-01` | new-collector | 0 | 8/0/0/0 | 1 | `41e35cfc769c7cb29082c85b6a8428becf4a0fc4e224545725fc7084a3b2caf9` | qualified-interval |
| `pipeline-source-red-01` | new-collector | 1 | 1/1/0/0 | 1 | `4043f2734fc6cf054de6c6e2f8eaa6de359a60d47c1bc6dab60e808741d3fcce` | assertion-failure |
| `fixture-isolation-retirement-01` | new-collector | 1 | 1/0/1/0 | 1 | `df05af4cf7d1eb91c65ac2b35e78250af6e886d0921e3bc3bd2851f3369b28d2` | test-error |
| `fixture-isolation-retirement-02` | new-collector | 0 | 1/0/0/0 | 1 | `262b09c519ee34e72b0e2f773818e6f9d728b37674193243e308ff7d50438bb8` | qualified-interval |
| `pipeline-source-red-02` | new-collector | 1 | 1/1/0/0 | 1 | `262b09c519ee34e72b0e2f773818e6f9d728b37674193243e308ff7d50438bb8` | assertion-failure |
| `pipeline-source-green-01` | new-collector | 0 | 1/0/0/0 | 1 | `ea94c95c6f92290a3b9a1207838eb15a5af11e0f7b1e968a86c19d675cb85bbe` | qualified-interval |
| `pipeline-source-integrity-01` | new-collector | 0 | 3/0/0/0 | 1 | `bac4f156664f459d53456092a9b953cbf79890d88dd23d7b78b65e7986d167dc` | qualified-interval |
| `controlled-archives-red-01` | new-collector | 1 | 2/2/0/0 | 1 | `9b30e2ef2b9ee55d5be8b96de15c5b6b80115cbf75dcc81290ad56f6818f168f` | assertion-failure |
| `controlled-archives-green-01` | new-collector | 0 | 2/0/0/0 | 1 | `4238c24ab79dd26e6e686f945781dc6f0a3c4b3b735a74ab62545d017dd0866a` | qualified-interval |
| `generation-producer-red-01` | new-collector | 1 | 1/1/0/0 | 1 | `5be99d706e98bcf3b01204ba717f9d3e536a33bbfc52355bd1ba56de4551c70b` | assertion-failure |
| `generation-producer-green-01` | new-collector | 0 | 1/0/0/0 | 1 | `ba50bec0a394a4c4e813132fe6d079bf10b025ee2d03b1081506ba8c471ff6b5` | qualified-interval |
| `session-abort-red-01` | new-collector | 1 | 3/3/0/0 | 1 | `89c310d5f305cc874c67d43fd4b9c028cb37df40629dd691aac692d3b6b89026` | assertion-failure |
| `session-abort-green-01` | new-collector | 0 | 3/0/0/0 | 1 | `a7954212f621517a70c821452634f74a31ab489d9779a33b0728a51cd3eafa8a` | qualified-interval |
| `initial-settings-red-01` | new-collector | 1 | 0/0/0/0 | 0 | `78b185a7ccb407df961904246ee3c2b4a7eefe7b3c0de37fe03551c0decca35b` | pretest-compile-failure |
| `initial-settings-red-02` | new-collector | 1 | 2/2/0/0 | 1 | `b1f4858519e3c7f5089129e3e8e7a9d407c28c0e6850c5651c727a39709e8440` | assertion-failure |
| `initial-settings-green-01` | new-collector | 0 | 2/0/0/0 | 1 | `42eca6d9c59a569d79659ee4788dcd127aa3f141d351dcedc88fc1be2c96bdb8` | qualified-interval |
| `initial-settings-boundaries-01` | new-collector | 0 | 6/0/0/0 | 1 | `005550f1f7afd813155ee157e23b96fb9f896879730cd4daa0510b63886e1a0d` | qualified-interval |
| `generation-prepare-red-01` | new-collector | 1 | 1/1/0/0 | 1 | `a4022de7173f3f5a96e212be194141cee85426b8421eccf1b42abdb26089c1c7` | assertion-failure |
| `generation-prepare-green-01` | new-collector | 0 | 1/0/0/0 | 1 | `feb188e5a2c33cafa28b05a4c726a710a9b51eb7a7b55de4431b38605fcd0c2d` | qualified-interval |
| `generation-queue-red-01` | new-collector | 1 | 1/1/0/0 | 1 | `69266617995dee94739ab4f1963636af70069fc4f63e8e18d283b3df1edb094a` | assertion-failure |
| `generation-queue-green-01` | new-collector | 0 | 1/0/0/0 | 1 | `3f4372b40f40f67ecca1b0ae86d01cabe1901fffd8a45179e416266f961d8cc9` | qualified-interval |
| `graph-current-read-red-01` | new-collector | 1 | 6/2/0/0 | 1 | `0680ad75958b883a5c9dfa9207aca6fa54bc49a44331532860b558d2e74baef4` | assertion-failure |
| `graph-current-read-green-01` | new-collector | 0 | 6/0/0/0 | 1 | `4b2908e5861371eefdfba8f8ed2f9457620dfe40cb65f76c21035855fda085ae` | qualified-interval |
| `generation-queue-claim-01` | new-collector | 0 | 1/0/0/0 | 1 | `658db3e391ca3784585a5b68c587119f2bf70e72b41e47203802631a29d2b8ba` | qualified-interval |
| `generation-stored-binding-red-01` | new-collector | 1 | 1/0/1/0 | 1 | `67e781622c370421bf94abeedf062d62f533729438603805cf692bf0992dc4dd` | test-error |
| `generation-stored-binding-green-01` | new-collector | 0 | 1/0/0/0 | 1 | `13cff1860c4ce6ac875919a422a16307742c3933174dfeb88b4b712ae4a672c9` | qualified-interval |
| `candidate-plan-red-01` | new-collector | 1 | 0/0/0/0 | 0 | `887f1322da5d3a39b0a589c8f9cb93725c3c2b57f14f25fe85155a839c091747` | pretest-compile-failure |
| `candidate-plan-red-02` | new-collector | 1 | 2/2/0/0 | 1 | `ba015aebbf4b5df8f7c2af798d1589f9e4538e7b48fd7c340219a11cc211be12` | assertion-failure |
| `candidate-plan-green-01` | new-collector | 1 | 2/0/2/0 | 1 | `3fdf8af417fca3a17ab610a9133855a6cc9815c8566ad462815ae485f80f314d` | test-error |
| `candidate-plan-green-02` | new-collector | 0 | 2/0/0/0 | 1 | `1306a53948bdf2185f5ca32d38a6e29cfd243952daaf83947924abc9f3eddfd8` | qualified-interval |
| `generation-worker-prepare-red-01` | new-collector | 1 | 1/0/1/0 | 1 | `73b5d831b058cf73a8b24cf3c49aa933b4d7bf2e61cc0e674dcd20085dd95539` | test-error |
| `generation-worker-prepare-green-01` | new-collector | 0 | 1/0/0/0 | 1 | `3e8cc3f04b863d7f150260c7ab01fe975b833d6223cbfcd9ea12c0dabe95085f` | qualified-interval |
| `controlled-client-red-01` | new-collector | 1 | 2/2/0/0 | 1 | `c2c2e50015bf634ab03cb1d578ca337d8df7da851aef93f65bfd288294a5e09f` | assertion-failure |
| `controlled-client-green-01` | new-collector | 1 | 0/0/0/0 | 0 | `0a7a4b156e34dd171c9ac16fe75ed0b62fe1c3fa0363cb435a3bcb445df86a65` | pretest-compile-failure |
| `controlled-client-green-02` | new-collector | 0 | 2/0/0/0 | 1 | `e070f598e15dc7066dc846533b1210c290832421469d4c1ea2985df0af803449` | qualified-interval |
| `generation-external-red-01` | new-collector | 1 | 1/1/0/0 | 1 | `c0c1ad2b778616f1ac8fea246b27922748cfac02724f9eb04d5d942d65409466` | assertion-failure |
| `generation-external-green-01` | new-collector | 1 | 0/0/0/0 | 0 | `55d02460d0cba32f59ed9721fde4b378dd5b6d24cac0958fa028a646914214d7` | pretest-compile-failure |
| `generation-external-green-02` | new-collector | 1 | 1/1/0/0 | 1 | `5fa31e93d9089b6a2a8db727936eb4e23b1ffa36525fd11426c99b5282ee8a13` | assertion-failure |
| `original-external-recovery-01` | new-collector | 1 | 0/0/0/0 | 0 | `0eb0ae2e755bbef1b3cc7f7e4dea0aff023cfe1f41cd11ae5e930d858d1f175a` | pretest-compile-failure |
| `original-external-recovery-02` | new-collector | 1 | 1/0/1/0 | 1 | `5cc537ea3b53cc66ab4d6562817754efb9e79866c023d0e7aae2490a725cbd69` | test-error |
| `original-external-recovery-03` | new-collector | 1 | 1/0/1/0 | 1 | `13667afe1fbd47b5a299369145545dfe676a72eb8e52f3eb1335c182cc6663cf` | test-error |
| `original-external-recovery-04` | new-collector | 1 | 1/0/1/0 | 1 | `b7afeeec8d1ddfa135bdf8311f1a4c3e4ff5f8544e6c625fb885f4fe46bfb1d2` | test-error |
| `original-external-recovery-05` | new-collector | 0 | 1/0/0/0 | 1 | `e72dcf4ec94fdf42ba1aec7fb9c7acf0f4156a8ce65fa5e673523bf4d460b07e` | qualified-interval |
| `generation-external-green-03` | new-collector | 0 | 1/0/0/0 | 1 | `bf45e17f2bffa86b712b444330cfe38e73d17677a015ad7845b0775ed484ced1` | qualified-interval |
| `candidate-reader-red-01` | new-collector | 1 | 1/0/1/0 | 1 | `0cf8f61b5f4e8c078d029aaa1b67a198642503b81998411e76ffbe2c26fcda0f` | test-error |
| `candidate-reader-green-01` | new-collector | 0 | 1/0/0/0 | 1 | `3dc573360985fef8a9e3adc83e71c1187dc1e6a1aa8daa8f358c78b7bdad0fac` | qualified-interval |
| `generation-rich-red-01` | new-collector | 1 | 1/1/0/0 | 1 | `6e744a1f1718b9176c18564cdf58aaabbc77a838722eeb700f455a3906c62bbf` | assertion-failure |
| `generation-rich-green-01` | new-collector | 1 | 1/1/0/0 | 1 | `12816f17467a5204d7ea86d41619282bd6fba619e3fdb965a8a1fa23a05e0f2b` | assertion-failure |
| `generation-rich-green-02` | new-collector | 0 | 1/0/0/0 | 1 | `6f92f54672904431ac0e0e1f5b63b88d2ebaae08394104298d463a1f6f2f2960` | qualified-interval |
| `generation-terminal-replay-red-01` | new-collector | 1 | 1/0/1/0 | 1 | `010b37eab4241114609dc729d06cabd9a0987c12191d15e879094d1c18671038` | test-error |
| `generation-terminal-replay-green-01` | new-collector | 0 | 1/0/0/0 | 1 | `a7a033548329773092c38385b7ef4cb8b821af03ecd5920f1eff8c6d4ba0414b` | qualified-interval |
| `generation-provider-restart-01` | new-collector | 0 | 1/0/0/0 | 1 | `d19976ffd8cf2e99f2afd8441cca9057027ac0cf9a2f298fa5fad8ba35f2bcda` | qualified-interval |
| `generation-business-stale-red-01` | new-collector | 1 | 1/0/1/0 | 1 | `9d7e6f3a0b6caf01e703a75a92f7230109a2c955d8e993ec2685ba9368e0a588` | test-error |
| `generation-business-stale-green-01` | new-collector | 0 | 1/0/0/0 | 1 | `ea984ea2fe1c0a6286e191294ad95f3f81674eba7c0e5d335e6e1366941c7429` | qualified-interval |
| `application-provider-restart-01` | new-collector | 1 | 0/0/0/0 | 0 | `6f5cdc3546487497591403e3b00c62846317bab474fcaca7685a4470faf5efa5` | pretest-compile-failure |
| `application-provider-restart-02` | new-collector | 0 | 1/0/0/0 | 1 | `4751922528fad85da5aa625e5da4adcc0103761caac0ba7781e50d6148df76db` | excluded |
| `application-provider-restart-03` | new-collector | 0 | 1/0/0/0 | 1 | `4b90ce50300cd67322ba265b2e4067e76aac6a0f53da6967268e11f49ac856a7` | qualified-interval |
| `generation-stale-replay-red-01` | new-collector | 1 | 1/1/0/0 | 1 | `8b821bd8196f4b24a6eb52846d381e3bd8506d144969dc1f4966288af9a77e00` | assertion-failure |
| `generation-stale-replay-green-01` | new-collector | 0 | 1/0/0/0 | 1 | `2035dc8ae619c4e57a67b3de4beba58ee8b46f60cc8f6a014bee9af978092ed8` | qualified-interval |
| `generation-insert-rollback-01` | new-collector | 0 | 1/0/0/0 | 1 | `708236baee677b6d1f5e1471b54a1bb39f1925d19e077c3ef0d06bbf49844ae2` | qualified-interval |
| `generation-readback-rollback-01` | new-collector | 0 | 1/0/0/0 | 1 | `708236baee677b6d1f5e1471b54a1bb39f1925d19e077c3ef0d06bbf49844ae2` | qualified-interval |
| `generation-audit-rollback-01` | new-collector | 0 | 1/0/0/0 | 1 | `708236baee677b6d1f5e1471b54a1bb39f1925d19e077c3ef0d06bbf49844ae2` | qualified-interval |
| `generation-outbox-rollback-01` | new-collector | 0 | 1/0/0/0 | 1 | `708236baee677b6d1f5e1471b54a1bb39f1925d19e077c3ef0d06bbf49844ae2` | qualified-interval |
| `generation-review-reader-red-01` | new-collector | 1 | 0/0/0/0 | 0 | `3ff22f20ec39313ceec5a9cbb76585d1bd3f939983340940c0274eca2b24f823` | pretest-compile-failure |
| `generation-review-reader-red-02` | new-collector | 1 | 1/0/1/0 | 1 | `60f9bce1bff17ae8ad8c0364903b3b47e9db09cefea76a87053a79ae9b7e9053` | test-error |
| `generation-review-reader-green-01` | new-collector | 0 | 1/0/0/0 | 1 | `192d05be3c8c6cb55b137912dc84f2f61d9d31c88a185feae9a37f53f28bd7a6` | qualified-interval |
| `generation-review-identities-red-01` | new-collector | 1 | 1/1/0/0 | 1 | `45c112adabfbeb28ce2bfea345790401f0d0d2f12d1e447cadd762e7e7d0325c` | assertion-failure |
| `generation-review-identities-green-01` | new-collector | 0 | 1/0/0/0 | 1 | `eed104a0ff77d61cc83584d9b9b2e895b838bb910bb2983c4367104c84c98ffa` | qualified-interval |
| `generation-final-reader-red-01` | new-collector | 1 | 1/0/1/0 | 1 | `e5d95efc3c39852263df3c9213ad753dbf7c7f3bfc44731659b49b68790d4b4a` | test-error |
| `generation-final-reader-green-01` | new-collector | 0 | 1/0/0/0 | 1 | `82bf8d9c26d40c92eaac8d58d1cec605061379f1d9f643701f6e79110455d285` | qualified-interval |
| `generation-invalid-terminal-replay-01` | new-collector | 0 | 1/0/0/0 | 1 | `174953f2f8c386965dad7259ad0919796bd29dd6ecff38600bf14eaf2354908f` | qualified-interval |
| `minutes-http-red-01` | new-collector | 1 | 1/1/0/0 | 1 | `ee30812c568f1ff20e7745442e7d7ce20221686c8fedbc1eb3a86c5d4d1a2c3b` | assertion-failure |
| `minutes-http-green-01` | new-collector | 0 | 1/0/0/0 | 1 | `6dfef354a0d18a9eee213508ab588c86c492ca09c6458db1c9bdbe8fd95bbfe3` | qualified-interval |
| `composite-recovery-red-01` | new-collector | 1 | 1/1/0/0 | 1 | `4b0f8b463fbce06f0c3a174b13ee0cf36dc45f334017f45ce38af9e1bc87717e` | assertion-failure |
| `composite-recovery-green-01` | new-collector | 0 | 1/0/0/0 | 1 | `ff54eb258d5fe7d2de41805a23b098e10063afccd0fa1150159b51cc89540690` | qualified-interval |
| `controlled-admin-red-01` | new-collector | 1 | 1/1/0/0 | 1 | `f9a399aa7ddbdbf1dd703c70de974358cec3e3fa15f465ceb827a005061251db` | assertion-failure |
| `controlled-admin-green-01` | new-collector | 0 | 1/0/0/0 | 1 | `bee725f4f948aef580277a0270f750d1f812eabe5f84e2b3f51533d3c7e9c569` | qualified-interval |
| `minutes-pagination-red-01` | new-collector | 1 | 1/1/0/0 | 1 | `356a26037bb7b884c5047af01b9fe277f44faa3ae0f18fc24cc11a6f7f09ef73` | assertion-failure |
| `minutes-pagination-green-01` | new-collector | 0 | 1/0/0/0 | 1 | `b583cef32366050f1398331b650b27e4273aa4f85b0d2d701a90459e75edeac4` | qualified-interval |
| `minutes-http-boundaries-red-01` | new-collector | 1 | 1/0/1/0 | 1 | `09a0b6500f904b475614b433fa4510180e1d281b0f9d7684e871e21365a3a196` | test-error |
| `minutes-http-boundaries-green-01` | new-collector | 0 | 1/0/0/0 | 1 | `bc7a3af4fe70226078f72344e6df1ee63c975e8fdb98a5a9c9ac549790518b36` | qualified-interval |
| `generation-zero-effect-replay-red-01` | new-collector | 1 | 1/1/0/0 | 1 | `d0eab5515e6daf8cdd913c2236ad2500d85d58984d914ced9a40dec2b80a18da` | assertion-failure |
| `client-zero-proof-red-01` | new-collector | 1 | 3/0/1/0 | 1 | `f657429abd99971e4ee39cedd76a97b0760dfab36194149778aa2cfffcf8a192` | test-error |
| `client-zero-proof-red-02` | new-collector | 1 | 3/1/0/0 | 1 | `6145663f2b567642b64a140a8d2e0da196ddffbfc08e72a830279fe58f672476` | assertion-failure |
| `client-zero-proof-green-01` | new-collector | 0 | 3/0/0/0 | 1 | `4aaa3e1d4c3f97f295feea007700ab34059c3532adee3ba2ac8efc5f8bc64d55` | qualified-interval |
| `generation-zero-effect-replay-green-01` | new-collector | 0 | 1/0/0/0 | 1 | `4aaa3e1d4c3f97f295feea007700ab34059c3532adee3ba2ac8efc5f8bc64d55` | qualified-interval |
| `denied-fact-policy-red-01` | new-collector | 1 | 0/0/0/0 | 0 | `be2ea14b050c91feef8cb21f58a7990548c18a244a010e7b94eab6fcd6e039b9` | pretest-compile-failure |
| `denied-fact-policy-red-02` | new-collector | 1 | 1/0/1/0 | 1 | `2936edd53c9be0249ff9a2834e941ebee79cfbb8f395659472753b8a749db94b` | test-error |
| `denied-fact-policy-green-01` | new-collector | 0 | 1/0/0/0 | 1 | `72e7c215a8700b3dfa6b2575645edc3365a247cdc2fb10e9a619d8eab2290d37` | qualified-interval |
| `generation-revoked-consumer-01` | new-collector | 0 | 1/0/0/0 | 1 | `5523de5da1c1c0cf08ad5413171d164889c369b09b41a78a8dca178664291e64` | qualified-interval |
| `generation-deleted-consumer-01` | new-collector | 0 | 1/0/0/0 | 1 | `ee2368a1d7bb00148ba409860c828645b2c832e8cb19b157c600775a9060a48e` | qualified-interval |
| `generation-physical-cancel-01` | new-collector | 0 | 1/0/0/0 | 1 | `44bb37e93d5a46ce8b83563f93326debd8559a3d3efa4e38c7679e61cca33d31` | qualified-interval |
| `execution-manifest-bound-red-01` | new-collector | 1 | 1/1/0/0 | 1 | `fb186ef72945d559730b22fa1c6444661da830d7409526ef218f0ecfbe252644` | assertion-failure |
| `execution-manifest-bound-green-01` | new-collector | 0 | 1/0/0/0 | 1 | `e68fa63557478804db5872a2d664da751fd12835e691e9103139bf1c53aa51d8` | qualified-interval |
| `denied-lookup-red-01` | new-collector | 1 | 1/1/0/0 | 1 | `2c174bd9994045145c95ad5581a43e828b8fada83cbe226cf7b934d1c8b33c9b` | assertion-failure |
| `denied-lookup-green-01` | new-collector | 1 | 0/0/0/0 | 0 | `2376ba7b3fd646dfeeae6fafff2c3bccd7c6cdb5a9e3889edc7c100420881558` | pretest-compile-failure |
| `denied-lookup-green-02` | new-collector | 1 | 1/1/0/0 | 1 | `8d8ec6b00e33c966a3d2d6dedf2d1ad1aca5bad4b7d201f2a7bb27ecfce0ff89` | assertion-failure |
| `denied-original-recovery-01` | new-collector | 1 | 1/1/0/0 | 1 | `b69dc5273e76d146d68dee37b0c3a33d187618fd271c140095b2ce7a0c2cba76` | assertion-failure |
| `denied-original-recovery-02` | new-collector | 0 | 1/0/0/0 | 1 | `a69bc28511fd00c5ff1390a3c2e3136b82aeace94a166206cc56518d8963881c` | qualified-interval |
| `denied-lookup-green-03` | new-collector | 1 | 1/1/0/0 | 1 | `0d21334812e808623e8dec525920d0d3c41351fc827d4b201776d722f532f45f` | assertion-failure |
| `denied-child-settlement-01` | new-collector | 0 | 1/0/0/0 | 1 | `2b38ab9c9410bad48fc3c8f427981a0bc19b9360167e60e67e373184841b963d` | qualified-interval |
| `denied-lookup-green-04` | new-collector | 0 | 1/0/0/0 | 1 | `8a1ba758c6d8005f2fd62d18304c298c32d1a00e62831767846f8b514b2366de` | qualified-interval |
| `deleted-lookup-green-01` | new-collector | 0 | 1/0/0/0 | 1 | `8a1ba758c6d8005f2fd62d18304c298c32d1a00e62831767846f8b514b2366de` | qualified-interval |
| `lookup-authority-green-01` | new-collector | 0 | 1/0/0/0 | 1 | `8204beb5a78bde96a160ec696bddddefd79a3e119e0d1f1652a422cf075af815` | qualified-interval |
| `pinned-render-red-01` | new-collector | 1 | 1/1/0/0 | 1 | `e4b7b6d376f5d6817df68faf0636bef5e715553ec39754d7217f9eab282d66a1` | assertion-failure |
| `pinned-render-green-01` | new-collector | 1 | 0/0/0/0 | 0 | `79b5d05eae9e93ee53a657695880d0c2d45f047ccb813e26ae552c4d31f6aff4` | pretest-compile-failure |
| `pinned-render-green-02` | new-collector | 1 | 0/0/0/0 | 0 | `805392e1636b7a20c3d5300d04558b55a1857a0f295fddcd152eae331f4c933e` | pretest-compile-failure |
| `pinned-render-green-03` | new-collector | 1 | 1/0/1/0 | 1 | `096a3ef3d416719bc50b367f868cdf7529f760c8379e77ac7ccede1ae9e5e190` | test-error |
| `pinned-render-diagnostic-01` | new-collector | 1 | 1/0/1/0 | 1 | `f89b34f96cbc7e217a69558b8943639a1f1aac88cea9e2814ea4731eb6f542a1` | test-error |
| `pinned-render-green-04` | new-collector | 0 | 1/0/0/0 | 1 | `e2adb21f20ad0842ac3d48815c74dcf6daa5bd4b4765129de814963fe021e221` | qualified-interval |
| `archive-append-red-01` | new-collector | 1 | 1/1/0/0 | 1 | `b5b1103810b90235ff4d86f0afca68e96c1ff331f8aa9690ecb0eb69fddbe443` | assertion-failure |
| `archive-append-red-02` | new-collector | 1 | 1/1/0/0 | 1 | `992b88c5f77b81d438eff5ff2219979d4bbb3633d448599883c8a83913c4a207` | assertion-failure |
| `archive-append-green-01` | new-collector | 0 | 1/0/0/0 | 1 | `35693d8973c16902c77c61a9483b411adbb8a1dd094296343de2f30a516c6937` | qualified-interval |
| `observation-commit-ack-red-01` | new-collector | 1 | 1/0/1/0 | 1 | `58869f0eae3d1ed1fcbfafab2b901d6c5a21980706058368bb74306da88d8e30` | test-error |
| `observation-commit-ack-green-01` | new-collector | 0 | 1/0/0/0 | 1 | `e3281d8831b921130891f5b4e323e8004e4cda875ed2a8c9981f7eb649d99d93` | qualified-interval |
| `generation-unknown-consumer-01` | new-collector | 0 | 1/0/0/0 | 1 | `6e02547b24e6f564646c04cf887dfcdc85dc6f103ac64d4c9bf87dbafbf567fb` | qualified-interval |
| `minutes-response-budget-red-01` | new-collector | 1 | 1/1/0/0 | 1 | `86d0a7cb5d7b58137ae21d5934adc5af4b2942e98b5e0a1291cf9a320024e1b0` | assertion-failure |
| `minutes-response-budget-green-01` | new-collector | 1 | 1/1/0/0 | 1 | `3c624741e536a90a88cda47151bd80df850b77523ba68eab4a3120205ef59a55` | assertion-failure |
| `minutes-response-budget-green-02` | new-collector | 0 | 1/0/0/0 | 1 | `ca44d944b9e2801c0f2e99792925b8cffd4f1c494f5759a158915b5766997929` | excluded |
| `minutes-response-budget-green-03` | new-collector | 0 | 1/0/0/0 | 1 | `70c41bebd6f827628ba52ce01e6d0579dd6c58a01e48971a29dcf6686cff725d` | qualified-interval |
| `preparation-identity-union-red-01` | new-collector | 1 | 1/1/0/0 | 1 | `36d56ead6db485cd46b851badeda3722356f7ba772e13f8756c368567e90ca70` | assertion-failure |
| `preparation-identity-union-green-01` | new-collector | 0 | 1/0/0/0 | 1 | `91acfd4403774ab6232f2f1b6612a7091a522449f93436b4a430ee44edae0dae` | qualified-interval |
| `remaining-bounds-replay-red-01` | new-collector | 1 | 2/2/0/0 | 2 | `ca1482c355b9c31b85ed00a5d168d1e0aeb1e53c882a05e5ec740ac8df0961a6` | assertion-failure |
| `remaining-disposition-red-01` | new-collector | 1 | 0/0/0/0 | 0 | `5b34e08e4965e5fcb6a17974c92f0896b096b852d60b654c5cc7c9aaf2626106` | pretest-compile-failure |
| `remaining-disposition-red-02` | new-collector | 1 | 5/0/5/0 | 5 | `001648d7f90a3f77a27ad4db9c6d1dbb6eeb2569111fc9f2f3e3dbe6b933f4ac` | test-error |
| `remaining-disposition-red-03` | new-collector | 1 | 5/3/0/0 | 5 | `d15cf02de811ad48ed6456585f983bf5567b5a0eabe097c9217332971e688358` | assertion-failure |
| `remaining-disposition-green-01` | new-collector | 0 | 5/0/0/0 | 5 | `9cc03e991c444ae7dd6681b511bbfddce71469111033927206a6301c4bad7142` | qualified-interval |
| `output-admission-red-01` | new-collector | 1 | 4/1/0/0 | 1 | `2b20298e38028321e42d0b38f0a6d7b5ec5ddafe5b7b65fa3339f14a34a359ab` | assertion-failure |
| `discovery-limits-red-01` | new-collector | 1 | 1/1/0/0 | 1 | `2b20298e38028321e42d0b38f0a6d7b5ec5ddafe5b7b65fa3339f14a34a359ab` | assertion-failure |
| `output-admission-green-01` | new-collector | 0 | 4/0/0/0 | 1 | `8c03660764592d22f99c4486c1ecf2ce8677a4c2b1d0c56b94c3d4c1a9958bb0` | qualified-interval |
| `remaining-limits-mixed-01` | new-collector | 1 | 3/1/0/0 | 3 | `fc04574b716ae4f427bd554b778b327b255a169df68cfa97a39a31d0d2588390` | assertion-failure |
| `remaining-limits-green-01` | new-collector | 0 | 3/0/0/0 | 3 | `50fb36010bbccd9d8f8f16b91e207ee2e28b3a0166f3915db23226aa93916b98` | qualified-interval |

## Independent selected-case matrix

The last qualified window is chosen by recorded start time; earlier versions and all RED/ERROR observations remain in the JSON matrix. No selected key ends with an unresolved failure/error within this fixed historical set; the final source has not yet been frozen or qualified.

| Class#method | Observations | Last qualified window | Latest window/status |
| --- | ---: | --- | --- |
| `ama.config.json.Task09CandidatePlanTest#completeCandidatePlanRemapsEveryTableAndGrantsNoAcceptedActionOrReview` | 3 | `candidate-plan-green-02` | `candidate-plan-green-02` PASS |
| `ama.config.json.Task09CandidatePlanTest#independentPlansUseDifferentServerIdsAndOnlyHistorySlotsAreBoundLater` | 3 | `candidate-plan-green-02` | `candidate-plan-green-02` PASS |
| `ama.config.json.Task09ControlledArchivesTest#actualTemplateIdentityChangesBothRootBindingsWithoutChangingSharedCodec` | 2 | `controlled-archives-green-01` | `controlled-archives-green-01` PASS |
| `ama.config.json.Task09ControlledArchivesTest#completeFifteenProfilesAndBothRootsUseStrictCanonicalOriginalBytes` | 2 | `controlled-archives-green-01` | `controlled-archives-green-01` PASS |
| `ama.config.json.Task09ControlledClientTest#lookupOfNeverSentExecutionIsUnknownAndDoesNotCreateProviderReservation` | 5 | `client-zero-proof-green-01` | `client-zero-proof-green-01` PASS |
| `ama.config.json.Task09ControlledClientTest#postRejectionWithoutPersistedLookupProofCannotIssueZeroEffectReceipt` | 3 | `client-zero-proof-green-01` | `client-zero-proof-green-01` PASS |
| `ama.config.json.Task09ControlledClientTest#realAuthenticatedSuccessBindsMetadataAndExactOutputToPrivateRequest` | 5 | `client-zero-proof-green-01` | `client-zero-proof-green-01` PASS |
| `ama.config.json.Task09ControlledEnvelopeStoreTest#changedEnvelopeWithOldHashesNeverChangesExistingMapping` | 2 | `envelope-store-green-01` | `envelope-store-green-01` PASS |
| `ama.config.json.Task09ControlledEnvelopeStoreTest#corruptEnvelopeCannotBeRepairedByDuplicatePost` | 2 | `envelope-store-green-01` | `envelope-store-green-01` PASS |
| `ama.config.json.Task09ControlledEnvelopeStoreTest#exactCanonicalEnvelopeAndPayloadAreValidatedAfterReopen` | 2 | `envelope-store-green-01` | `envelope-store-green-01` PASS |
| `ama.config.json.Task09ControlledEnvelopeStoreTest#lookupLeavesReservedExecutionUnknownAcrossReopen` | 2 | `envelope-store-green-01` | `envelope-store-green-01` PASS |
| `ama.config.json.Task09ControlledHttpTest#actualHttpDuplicateRetainsOneDurableResult` | 3 | `provider-http-boundaries-01` | `provider-http-boundaries-01` PASS |
| `ama.config.json.Task09ControlledHttpTest#aggregateAndPerFieldLimitsRejectBeforeUnboundedParse` | 3 | `provider-http-boundaries-01` | `provider-http-boundaries-01` PASS |
| `ama.config.json.Task09ControlledHttpTest#exactAggregateHeaderMaximumSucceedsAndOneMoreByteFails` | 1 | `provider-http-boundaries-01` | `provider-http-boundaries-01` PASS |
| `ama.config.json.Task09ControlledHttpTest#freshProviderProcessPreservesExactEnvelopeAndResultIdentity` | 3 | `provider-http-boundaries-01` | `provider-http-boundaries-01` PASS |
| `ama.config.json.Task09ControlledHttpTest#rawRepeatedCaseDuplicateCombinedAndFoldedEnvelopeRejectedBeforeReservation` | 3 | `provider-http-boundaries-01` | `provider-http-boundaries-01` PASS |
| `ama.config.json.Task09ControlledHttpTest#unauthenticatedRequestCannotReserve` | 3 | `provider-http-boundaries-01` | `provider-http-boundaries-01` PASS |
| `ama.config.json.Task09ControlledProtocolTest#recomputesRealV2InputAndLogicalEnvelopeInsteadOfComparingSuppliedHashes` | 2 | `protocol-green-02` | `protocol-green-02` PASS |
| `ama.config.json.Task09ControlledProtocolTest#rejectsMissingCombinedPaddingNoncanonicalAndOversizeHeaders` | 2 | `protocol-green-02` | `protocol-green-02` PASS |
| `ama.config.json.Task09ControlledProtocolTest#rejectsNoncanonicalBodyDuplicateNumbersAndWrongExecution` | 2 | `protocol-green-02` | `protocol-green-02` PASS |
| `ama.config.json.Task09ControlledProtocolTest#rejectsWrongInputFingerprintAndDuplicateUnsortedRoots` | 2 | `protocol-green-02` | `protocol-green-02` PASS |
| `ama.config.json.Task09GenerationBindingTest#prospectiveBindingRetainsExactCatalogAndVerifiedDefinitionsWithoutOwnRow` | 2 | `bindings-green-01` | `bindings-green-01` PASS |
| `ama.config.json.Task09GenerationBindingTest#storedBindingRetainsSameDefinitionsAndCatalogAfterClosureFinish` | 2 | `bindings-green-01` | `bindings-green-01` PASS |
| `ama.config.json.Task09OutputAdmissionTest#lookupOfNeverSentExecutionIsUnknownAndDoesNotCreateProviderReservation` | 2 | `output-admission-green-01` | `output-admission-green-01` PASS |
| `ama.config.json.Task09OutputAdmissionTest#originalWireBudgetRejectsDurableSuccessBeforeResultGet` | 2 | `output-admission-green-01` | `output-admission-green-01` PASS |
| `ama.config.json.Task09OutputAdmissionTest#postRejectionWithoutPersistedLookupProofCannotIssueZeroEffectReceipt` | 2 | `output-admission-green-01` | `output-admission-green-01` PASS |
| `ama.config.json.Task09OutputAdmissionTest#realAuthenticatedSuccessBindsMetadataAndExactOutputToPrivateRequest` | 2 | `output-admission-green-01` | `output-admission-green-01` PASS |
| `ama.config.json.Task09ProducerTest#completeFrozenProducerEnvelopeIncludesEveryFenceAndHasExactProfile` | 2 | `generation-producer-green-01` | `generation-producer-green-01` PASS |
| `ama.minutes.Task09BootstrapIT#bootstrapOnlyAcknowledgedEmptySchemaThenNormalRerunZero` | 1 | `bootstrap-01` | `bootstrap-01` PASS |
| `ama.minutes.Task09ControlledAdminIT#explicitCommandInstallsRealTemplateReleaseAndCapacityWithPrivateLaunchFiles` | 2 | `controlled-admin-green-01` | `controlled-admin-green-01` PASS |
| `ama.minutes.Task09FixtureIsolationIT#retireCapturedReadyFixturesWithoutChangingAnyImmutableSpec` | 2 | `fixture-isolation-retirement-02` | `fixture-isolation-retirement-02` PASS |
| `ama.minutes.Task09GraphCaptureIT#authenticSameIssuerHolderCapturesProtectedBodyAndExactEmptySets` | 6 | `graph-current-read-green-01` | `graph-current-read-green-01` PASS |
| `ama.minutes.Task09GraphCaptureIT#childSetChangedAfterDiscoveryIsRejected` | 6 | `graph-current-read-green-01` | `graph-current-read-green-01` PASS |
| `ama.minutes.Task09GraphCaptureIT#foreignAndExpiredScopesNeverBecomeAmbientTransactionAuthority` | 6 | `graph-current-read-green-01` | `graph-current-read-green-01` PASS |
| `ama.minutes.Task09GraphCaptureIT#oversizedLongtextIsRejectedBeforeAnyBodySelect` | 6 | `graph-current-read-green-01` | `graph-current-read-green-01` PASS |
| `ama.minutes.Task09GraphCaptureIT#priorRepeatableReadSnapshotCannotHideNewChildMembership` | 2 | `graph-current-read-green-01` | `graph-current-read-green-01` PASS |
| `ama.minutes.Task09GraphCaptureIT#protectedContentTransferUsesCurrentReadAfterOldSnapshot` | 2 | `graph-current-read-green-01` | `graph-current-read-green-01` PASS |
| `ama.minutes.Task09GraphDiscoveryIT#currentControlRootsIncludeExactRosterMembershipAndNeverTransferDisplayText` | 3 | `graph-controls-green-02` | `graph-controls-green-02` PASS |
| `ama.minutes.Task09GraphDiscoveryIT#currentSettingsCaptureExactZeroExclusionMembers` | 5 | `graph-controls-green-02` | `graph-controls-green-02` PASS |
| `ama.minutes.Task09GraphDiscoveryIT#followsEveryBodyParentAndRetainsExplicitEmptyEvidenceSetsWithoutTextTransfer` | 5 | `graph-controls-green-02` | `graph-controls-green-02` PASS |
| `ama.minutes.Task09GraphDiscoveryIT#foreignCurrentControlPointerFailsWholeDiscovery` | 3 | `graph-controls-green-02` | `graph-controls-green-02` PASS |
| `ama.minutes.Task09GraphDiscoveryIT#overflowRejectsWholeGraphAndForeignMeetingIsNeverAdopted` | 5 | `graph-controls-green-02` | `graph-controls-green-02` PASS |
| `ama.minutes.Task09GraphDiscoveryIT#setEnumeratesEveryCategoryActionAndEmptyDirectoryMembership` | 5 | `graph-controls-green-02` | `graph-controls-green-02` PASS |
| `ama.minutes.Task09LimitsConnectionIT#deployedFactoryEnforcesUtcUtf8AndFiniteSessionWaits` | 2 | `remaining-limits-green-01` | `remaining-limits-green-01` PASS |
| `ama.minutes.Task09SourceGraphIT#currentControlRootsIncludeExactRosterMembershipAndNeverTransferDisplayText` | 3 | `graph-sources-green-01` | `graph-sources-green-01` PASS |
| `ama.minutes.Task09SourceGraphIT#currentSettingsCaptureExactZeroExclusionMembers` | 3 | `graph-sources-green-01` | `graph-sources-green-01` PASS |
| `ama.minutes.Task09SourceGraphIT#discoversWholeExternalHistoryWithoutLongtextBeforeAnyCapture` | 3 | `graph-sources-green-01` | `graph-sources-green-01` PASS |
| `ama.minutes.Task09SourceGraphIT#enumeratesOriginalSessionFileReferenceAndEveryTypedInputTaskAttempt` | 3 | `graph-sources-green-01` | `graph-sources-green-01` PASS |
| `ama.minutes.Task09SourceGraphIT#followsEveryBodyParentAndRetainsExplicitEmptyEvidenceSetsWithoutTextTransfer` | 3 | `graph-sources-green-01` | `graph-sources-green-01` PASS |
| `ama.minutes.Task09SourceGraphIT#foreignCurrentControlPointerFailsWholeDiscovery` | 3 | `graph-sources-green-01` | `graph-sources-green-01` PASS |
| `ama.minutes.Task09SourceGraphIT#overflowRejectsWholeGraphAndForeignMeetingIsNeverAdopted` | 3 | `graph-sources-green-01` | `graph-sources-green-01` PASS |
| `ama.minutes.Task09SourceGraphIT#setEnumeratesEveryCategoryActionAndEmptyDirectoryMembership` | 3 | `graph-sources-green-01` | `graph-sources-green-01` PASS |
| `ama.minutes.Task09TemplateIT#conflictingAssignedIdentityCannotOverwriteOrLeavePartialRevision` | 2 | `template-green-01` | `template-green-01` PASS |
| `ama.minutes.Task09TemplateIT#explicitInstallCreatesActualParentRevisionAndExactSevenThemes` | 2 | `template-green-01` | `template-green-01` PASS |
| `ama.minutes.Task09TemplateIT#unselectedModeCannotConstructAnAdministrativeInstaller` | 2 | `template-green-01` | `template-green-01` PASS |
| `ama.minutes.json.Task09StoredAnchorTest#rejectsOversizeAndCancellation` | 2 | `anchors-green-01` | `anchors-green-01` PASS |
| `ama.minutes.json.Task09StoredAnchorTest#rejectsReorderedWhitespaceDuplicateUnknownAndTrailingStoredForms` | 2 | `anchors-green-01` | `anchors-green-01` PASS |
| `ama.minutes.json.Task09StoredAnchorTest#retainsExactScalarAnchorAndCanonicalStoredText` | 2 | `anchors-green-01` | `anchors-green-01` PASS |
| `ama.model.Task09ControlledStoreTest#corruptAcknowledgedResultFailsClosedOnReopen` | 2 | `provider-store-green-01` | `provider-store-green-01` PASS |
| `ama.model.Task09ControlledStoreTest#definitiveRejectionIsDurableAndPermanentlyProhibitsComputation` | 2 | `provider-store-green-01` | `provider-store-green-01` PASS |
| `ama.model.Task09ControlledStoreTest#exclusiveStoreLockRejectsSecondProcessOwner` | 2 | `provider-store-green-01` | `provider-store-green-01` PASS |
| `ama.model.Task09ControlledStoreTest#immutableLogicalAndExecutionTuplesRejectChangedBytes` | 2 | `provider-store-green-01` | `provider-store-green-01` PASS |
| `ama.model.Task09ControlledStoreTest#incompleteUnacknowledgedJournalTailPreservesCompletePrefix` | 2 | `provider-store-green-01` | `provider-store-green-01` PASS |
| `ama.model.Task09ControlledStoreTest#rejectedModeAndOversizeNeverReserve` | 2 | `provider-store-green-01` | `provider-store-green-01` PASS |
| `ama.model.Task09ControlledStoreTest#reserveAndSuccessSurviveRestartAndDuplicateReturnsExactIdentity` | 2 | `provider-store-green-01` | `provider-store-green-01` PASS |
| `ama.model.Task09ControlledStoreTest#reservedComputationSurvivesRestartAndLookupDoesNotCompute` | 2 | `provider-store-green-01` | `provider-store-green-01` PASS |
| `ama.model.Task09TransportAbortTest#cancellationClosesActualSocketDuringBodyReadAndCloseIsIdempotent` | 2 | `transport-abort-green-01` | `transport-abort-green-01` PASS |
| `ama.model.Task09TransportAbortTest#cancellationClosesActualSocketWhileWaitingForHeaders` | 2 | `transport-abort-green-01` | `transport-abort-green-01` PASS |
| `ama.model.Task09TransportAbortTest#readIdleDeadlineFiresWithoutAnyReceivedByte` | 2 | `transport-abort-green-01` | `transport-abort-green-01` PASS |
| `ama.storage.Task09CompositeRecoveryIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 2 | `composite-recovery-green-01` | `composite-recovery-green-01` PASS |
| `ama.storage.Task09DeletedLookupRecoveryIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 1 | `deleted-lookup-green-01` | `deleted-lookup-green-01` PASS |
| `ama.storage.Task09DeniedFactPolicyIT#disabledOwnerAllowsOnlyBoundFactRootCaptureWithoutBusinessOrSourceAuthority` | 2 | `denied-fact-policy-green-01` | `denied-fact-policy-green-01` PASS |
| `ama.storage.Task09DeniedLookupRecoveryIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 4 | `denied-lookup-green-04` | `denied-lookup-green-04` PASS |
| `ama.storage.Task09DeniedOriginalRecoveryIT#recoverOnlyOriginalFact` | 2 | `denied-original-recovery-02` | `denied-original-recovery-02` PASS |
| `ama.storage.Task09DeniedSettlementRecoveryIT#settleExistingTerminalFact` | 1 | `denied-child-settlement-01` | `denied-child-settlement-01` PASS |
| `ama.storage.Task09ExecutionManifestBudgetIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 2 | `execution-manifest-bound-green-01` | `execution-manifest-bound-green-01` PASS |
| `ama.storage.Task09GenerationApplicationRestartIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 2 | `application-provider-restart-03` | `application-provider-restart-03` PASS |
| `ama.storage.Task09GenerationArchiveAppendIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 3 | `archive-append-green-01` | `archive-append-green-01` PASS |
| `ama.storage.Task09GenerationAuditRollbackIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 1 | `generation-audit-rollback-01` | `generation-audit-rollback-01` PASS |
| `ama.storage.Task09GenerationCandidateInsertRollbackIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 1 | `generation-insert-rollback-01` | `generation-insert-rollback-01` PASS |
| `ama.storage.Task09GenerationCandidateReadbackRollbackIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 1 | `generation-readback-rollback-01` | `generation-readback-rollback-01` PASS |
| `ama.storage.Task09GenerationDeletedIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 1 | `generation-deleted-consumer-01` | `generation-deleted-consumer-01` PASS |
| `ama.storage.Task09GenerationExternalIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 5 | `candidate-reader-green-01` | `candidate-reader-green-01` PASS |
| `ama.storage.Task09GenerationFinalReaderIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 2 | `generation-final-reader-green-01` | `generation-final-reader-green-01` PASS |
| `ama.storage.Task09GenerationInvalidReplayIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 1 | `generation-invalid-terminal-replay-01` | `generation-invalid-terminal-replay-01` PASS |
| `ama.storage.Task09GenerationOutboxRollbackIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 1 | `generation-outbox-rollback-01` | `generation-outbox-rollback-01` PASS |
| `ama.storage.Task09GenerationPhysicalCancelIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 1 | `generation-physical-cancel-01` | `generation-physical-cancel-01` PASS |
| `ama.storage.Task09GenerationPreparationIT#realConfirmedAndInitializedFactsProduceProspectiveExactModelRequestWithoutInputRow` | 2 | `generation-prepare-green-01` | `generation-prepare-green-01` PASS |
| `ama.storage.Task09GenerationPreparationIdentitiesIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 2 | `preparation-identity-union-green-01` | `preparation-identity-union-green-01` PASS |
| `ama.storage.Task09GenerationQueueIT#acceptedQueueInputIsAtomicReplayFirstAndPreservesCurrentRoot` | 7 | `generation-worker-prepare-green-01` | `generation-worker-prepare-green-01` PASS |
| `ama.storage.Task09GenerationReplayIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 2 | `generation-terminal-replay-green-01` | `generation-terminal-replay-green-01` PASS |
| `ama.storage.Task09GenerationRestartIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 1 | `generation-provider-restart-01` | `generation-provider-restart-01` PASS |
| `ama.storage.Task09GenerationReviewIdentitiesIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 2 | `generation-review-identities-green-01` | `generation-review-identities-green-01` PASS |
| `ama.storage.Task09GenerationReviewReaderIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 2 | `generation-review-reader-green-01` | `generation-review-reader-green-01` PASS |
| `ama.storage.Task09GenerationRevokedIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 1 | `generation-revoked-consumer-01` | `generation-revoked-consumer-01` PASS |
| `ama.storage.Task09GenerationRichIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 3 | `generation-rich-green-02` | `generation-rich-green-02` PASS |
| `ama.storage.Task09GenerationStaleIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 2 | `generation-business-stale-green-01` | `generation-business-stale-green-01` PASS |
| `ama.storage.Task09GenerationStaleReplayIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 2 | `generation-stale-replay-green-01` | `generation-stale-replay-green-01` PASS |
| `ama.storage.Task09GenerationUnknownIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 1 | `generation-unknown-consumer-01` | `generation-unknown-consumer-01` PASS |
| `ama.storage.Task09GenerationZeroEffectReplayIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 2 | `generation-zero-effect-replay-green-01` | `generation-zero-effect-replay-green-01` PASS |
| `ama.storage.Task09InitialSettingsIT#auditFailureRollsBackEverySettingsEffect` | 1 | `initial-settings-boundaries-01` | `initial-settings-boundaries-01` PASS |
| `ama.storage.Task09InitialSettingsIT#committedReplayPrecedesNowUnavailableArchivesAndCurrentCas` | 3 | `initial-settings-boundaries-01` | `initial-settings-boundaries-01` PASS |
| `ama.storage.Task09InitialSettingsIT#concurrentSameKeyHasOneCompleteWinnerAndOneReplay` | 1 | `initial-settings-boundaries-01` | `initial-settings-boundaries-01` PASS |
| `ama.storage.Task09InitialSettingsIT#differentKeyAndDetachedHistoryCannotReinitialize` | 1 | `initial-settings-boundaries-01` | `initial-settings-boundaries-01` PASS |
| `ama.storage.Task09InitialSettingsIT#firstSettingsPersistRealTemplateEmptyExclusionAndAdvanceExactlyOnce` | 3 | `initial-settings-boundaries-01` | `initial-settings-boundaries-01` PASS |
| `ama.storage.Task09InitialSettingsIT#partialSettingsAreNotRepaired` | 1 | `initial-settings-boundaries-01` | `initial-settings-boundaries-01` PASS |
| `ama.storage.Task09LimitsDiscoveryIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 3 | `remaining-limits-green-01` | `remaining-limits-green-01` PASS |
| `ama.storage.Task09LimitsOutputIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 2 | `remaining-limits-green-01` | `remaining-limits-green-01` PASS |
| `ama.storage.Task09LookupAuthorityIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 1 | `lookup-authority-green-01` | `lookup-authority-green-01` PASS |
| `ama.storage.Task09MinutesHttpBoundaryIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 2 | `minutes-http-boundaries-green-01` | `minutes-http-boundaries-green-01` PASS |
| `ama.storage.Task09MinutesHttpIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 2 | `minutes-http-green-01` | `minutes-http-green-01` PASS |
| `ama.storage.Task09MinutesPaginationIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 2 | `minutes-pagination-green-01` | `minutes-pagination-green-01` PASS |
| `ama.storage.Task09MinutesResponseBudgetIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 4 | `minutes-response-budget-green-03` | `minutes-response-budget-green-03` PASS |
| `ama.storage.Task09ObservationCommitAckIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 2 | `observation-commit-ack-green-01` | `observation-commit-ack-green-01` PASS |
| `ama.storage.Task09OriginalExternalRecoveryIT#lookupSameOriginalFactThroughGenuineObserver` | 4 | `original-external-recovery-05` | `original-external-recovery-05` PASS |
| `ama.storage.Task09PinnedRenderIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 4 | `pinned-render-green-04` | `pinned-render-green-04` PASS |
| `ama.storage.Task09PipelineGraphIT#alteredConfirmedBodyCannotPassByMatchingStoredFingerprintAndTask` | 1 | `pipeline-source-integrity-01` | `pipeline-source-integrity-01` PASS |
| `ama.storage.Task09PipelineGraphIT#freshFinalAuthorizationTakesPrecedenceOverDeferredArchiveFailure` | 1 | `pipeline-source-integrity-01` | `pipeline-source-integrity-01` PASS |
| `ama.storage.Task09PipelineGraphIT#realImportSavedConfirmationPassesOriginalMinutesSourcePreparation` | 4 | `pipeline-source-integrity-01` | `pipeline-source-integrity-01` PASS |
| `ama.storage.Task09RemainingAdoptedIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 3 | `remaining-disposition-green-01` | `remaining-disposition-green-01` PASS |
| `ama.storage.Task09RemainingDisposedStaleIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 3 | `remaining-disposition-green-01` | `remaining-disposition-green-01` PASS |
| `ama.storage.Task09RemainingInitialReplayIT#replayLocksActualParentBeforeRevisionAndProvesEmptyExclusion` | 4 | `remaining-disposition-green-01` | `remaining-disposition-green-01` PASS |
| `ama.storage.Task09RemainingRejectedIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 3 | `remaining-disposition-green-01` | `remaining-disposition-green-01` PASS |
| `ama.storage.Task09RemainingTaskBudgetIT#genuineWorkerMarkerPermitHttpReceiptWritesCompletePrivateCandidate` | 4 | `remaining-disposition-green-01` | `remaining-disposition-green-01` PASS |
| `ama.worker.Task09SessionAbortTest#closePhysicallyAbortsHeldHeadersAndBodyWithoutSettlingUnknownPermit` | 2 | `session-abort-green-01` | `session-abort-green-01` PASS |
| `ama.worker.Task09SessionAbortTest#renewalLossPhysicallyAbortsHeldHeadersAndBodyWithoutRegainingSend` | 2 | `session-abort-green-01` | `session-abort-green-01` PASS |
| `ama.worker.Task09SessionAbortTest#singleRegistrationLateCancellationAndAbortRunOutsideMonitor` | 2 | `session-abort-green-01` | `session-abort-green-01` PASS |
