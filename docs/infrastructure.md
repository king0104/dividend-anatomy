# 인프라 현황 — 개인 k3s 클러스터

배포 시점(2026-08-24) 기준 클러스터 상태 스냅샷. 왜 이렇게 결정했는지는
`docs/decisions/08-deployment.md`, 그날그날 있었던 일은
`docs/progress-log.md`(Day 13) 참고 — 이 문서는 "지금 뭐가 어떻게 떠
있는지"만 담는 참조용이다. 바뀌면 이 문서도 같이 갱신한다.

## 클러스터 개요

OCI 테넌시(`king0104`, tenancy OCID
`ocid1.tenancy.oc1..aaaaaaaahierachuakaux2w6wn2767ggjhtbrtskgd4yerftotjwixelewnq`,
리전 `ap-chuncheon-1`) 안, `dividend-anatomy` 컴파트먼트가 아니라
**테넌시 루트 컴파트먼트**에 만들어져 있던 개인 k3s 클러스터. Always
Free `VM.Standard.A1.Flex`(Ampere ARM64) 3대로 4 OCPU/24GB 전체 무료
할당량을 다 씀 — 그래서 이 프로젝트 배포 때 새 인스턴스를 안 만들고
여기에 얹었다.

| 노드 | k8s 노드명 | 공인 IP | 사설 IP | 스펙 | 역할 |
|---|---|---|---|---|---|
| instance-20260227-1748 | `master` | 144.24.86.105 | 10.0.0.154 | 2 OCPU / 12GB | control-plane (taint 없음, 일반 pod도 스케줄됨) |
| instance-20260227-1801 | `worker-1` | 168.107.28.239 | 10.0.0.74 | 1 OCPU / 6GB | agent |
| instance-20260227-1800 | `worker-2` | 134.185.97.175 | 10.0.0.35 | 1 OCPU / 6GB | agent |

- k3s `v1.34.4+k3s1`, SSH는 `ubuntu@<공인 IP>` + `~/.ssh/id_ed25519`.
- kubectl은 각 노드에서 `sudo k3s kubectl ...`(별도 kubeconfig 배포
  안 해둠, 매번 SSH로 들어가서 씀).
- flannel 백엔드는 **`vxlan`**(기본값 유지 — 아래 "크로스노드
  네트워킹" 참고, `host-gw`는 시도했다가 되돌림).

## 네트워크

- VCN `vcn-20260226`(10.0.0.0/16), 테넌시 루트 컴파트먼트 소속.
  - `subnet-public`(10.0.0.0/24) — k3s 노드 3대가 여기 있음. 라우트
    테이블: "public route vcn-20260226"(인터넷 게이트웨이로 가는
    기본 라우트 하나만 — host-gw 실험 때 추가한 pod CIDR 라우트는
    실험 종료 후 제거함).
  - `subnet-private`(10.0.2.0/24) — dividend-anatomy MySQL이 여기
    있음.
  - 두 서브넷이 **같은 VCN**이라, MySQL 보안 목록이 "10.0.0.0/16
    전체"에 3306을 열어둔 덕에 클러스터에서 DB로 **Bastion 터널 없이
    직접 접속** 가능(`docs/decisions/08-deployment.md` 참고).
- k3s 노드들의 보안 목록: "Default Security List for
  vcn-20260226"(`aaaaaaaa3mwce2zum6nigaojggcfqtagjhba3oborol7afqironu5qnbk6na`).
  주요 인바운드 규칙:

  | 포트/프로토콜 | 소스 | 용도 |
  |---|---|---|
  | TCP 22 | 0.0.0.0/0 | SSH |
  | ICMP (frag/unreachable) | 0.0.0.0/0, 10.0.0.0/16 | 경로 MTU 등 |
  | TCP 30080 | 0.0.0.0/0 | `nginx-test` NodePort(기존) |
  | TCP 6443 | 10.0.0.0/16 | k3s API |
  | TCP 3306 | 10.0.0.0/16 | MySQL(VCN 내부만) |
  | TCP 30081 | 0.0.0.0/0 | dividend-anatomy NodePort |
  | UDP 8472 | 10.0.0.0/16 | flannel VXLAN(노드 간 오버레이) |

  VNIC엔 NSG가 하나도 안 붙어 있어서(전부 `nsg-ids: []`) 이 보안
  목록이 유일한 클라우드 레벨 방화벽이다.

- **중요 — 호스트 레벨 `iptables`도 별도로 관리된다.** OCI 보안
  목록을 열었다고 끝이 아니라, 각 노드 자체의 `iptables INPUT`
  체인에도 명시적으로 포트를 열어줘야 실제로 통한다(SSH·기존
  NodePort 30080·이번에 추가한 30081·UDP 8472 전부 호스트
  `iptables`에도 별도 `ACCEPT` 규칙이 있다). 이 클러스터엔 ufw 같은
  선언적 방화벽 도구가 없고 수동으로 `iptables -I INPUT ...` 해둔
  규칙들이 `netfilter-persistent`로 저장돼서 재부팅에도 남는
  방식이다. **다음에 새 포트를 열 때는 OCI 보안 목록과 호스트
  `iptables` 둘 다 열어야 한다** — 이번에 UDP 8472를 호스트
  `iptables`에서 빠뜨려서 며칠 헤맸다(`docs/decisions/08-deployment.md`
  참고).

## 크로스노드 네트워킹

한때 이 클러스터는 서로 다른 노드에 있는 pod끼리 통신이 안 되는
상태였다(원인: 위에서 말한 호스트 `iptables`가 UDP 8472를 막고
있었음). 지금은 고쳐져서 **pod가 어느 노드에 뜨든, 어느 노드의
공인 IP로 NodePort에 접속하든 정상 동작한다** — 레플리카를 3개로
늘려 노드 3대에 하나씩 분산시킨 뒤 세 노드 IP 전부로 접속해서
실제로 검증했다. 자세한 진단 과정은
`docs/decisions/08-deployment.md` 참고.

## 현재 떠 있는 워크로드

| 네임스페이스 | 이름 | 뜬 노드 | 비고 |
|---|---|---|---|
| `default` | `nginx-test` | master | 이 클러스터 최초 워크로드, NodePort 30080 |
| `kube-system` | `coredns`, `local-path-provisioner` | master | k3s 기본 구성 요소 |
| `monitoring` | Prometheus, Grafana, Alertmanager, kube-state-metrics | 대부분 master | `monitoring-prometheus-node-exporter`는 DaemonSet이라 3대 전부에 하나씩 |
| `dividend-anatomy` | `dividend-anatomy` | (스케줄러가 자유롭게 선택 — 현재 master) | 이 프로젝트, 아래 상세 |

## dividend-anatomy 배포 상세

- 네임스페이스 `dividend-anatomy`, 매니페스트는 레포의 `k8s/` 아래
  (`namespace.yaml`, `deployment.yaml`, `service.yaml`,
  `secret.yaml.example` — 실제 `secret.yaml`은 gitignore됨).
- 이미지: 레지스트리 없이 사이드로드 — 로컬(Apple Silicon)에서
  `docker build --platform linux/arm64 -t dividend-anatomy:latest .` →
  `docker save -o /tmp/dividend-anatomy.tar` → `scp`로 3개 노드
  전부에 전송 → 각 노드에서 `sudo k3s ctr images import
  /tmp/dividend-anatomy.tar`. `imagePullPolicy: Never`라 이 절차를
  건너뛰면 새 이미지로 재배포가 안 된다 — **코드가 바뀌면 이
  4단계를 3개 노드 전부에 수동으로 반복해야 한다**(자동 CI/CD
  없음, 의도적).
- 노출: `Service` `type: NodePort`, `port 80 → targetPort 8080`,
  `nodePort: 30081`. TLS 없음(HTTP 평문) — 데모/포트폴리오 목적의
  의도적 제약.
- DB 접속: `DB_HOST=10.0.2.201`, `DB_PORT=3306`, Bastion 터널 없이
  같은 VCN 안에서 직접 접속.
- 리소스: `requests: 250m CPU / 512Mi`, `limits: 500m CPU / 768Mi`,
  레플리카 1개(공유 클러스터라 과하게 안 잡음).
- 매니페스트 적용: 로컬에서 파일을 만들고, `cat k8s/*.yaml | ssh
  ubuntu@<master> "sudo k3s kubectl apply -f -"`로 stdin을 통해
  적용(scp 안 함) — 여러 문서를 한 번에 적용할 땐 `---` 구분자를
  꼭 넣어야 한다(안 넣으면 YAML 파서가 마지막 문서 필드로 덮어써서
  엉뚱한 에러가 난다).

## 재배포 절차 (코드가 바뀌었을 때)

1. 로컬에서 `docker build --platform linux/arm64 -t
   dividend-anatomy:latest .`
2. `docker save dividend-anatomy:latest -o /tmp/dividend-anatomy.tar`
3. `scp`로 3개 노드 전부에 전송
4. 각 노드에서 `sudo k3s ctr images import
   /tmp/dividend-anatomy.tar`
5. master에서 `sudo k3s kubectl rollout restart deployment
   dividend-anatomy -n dividend-anatomy` (이미지 태그가 `:latest`로
   고정이라 `apply`만으로는 새 이미지를 안 당겨오므로 명시적으로
   재시작해야 함)

## 알려진 제약

- TLS 없음 — 기존 클러스터 워크로드(`nginx-test`)와 동일 수준,
  의도적 범위 밖.
- 자동 CI/CD 없음 — 위 재배포 절차를 매번 수동으로.
- 이미지 사이드로드 방식이라 레지스트리 장애/용량 이슈는 없지만,
  3개 노드 동기화를 사람이 직접 챙겨야 한다.
- 호스트 `iptables`가 선언적으로 관리되지 않음 — 새 포트가 필요하면
  OCI 보안 목록 + 호스트 `iptables` 둘 다 손으로 열어야 한다(위
  "네트워크" 절 참고).
