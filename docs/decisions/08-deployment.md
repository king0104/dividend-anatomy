# 08. 배포 — 기존 개인 k3s 클러스터에 신규 워크로드로

**날짜**: 2026-08-24

## 왜 확인했나

PROJECT.md 마지막 남은 항목(배포)을 진행하려면 실행 환경이 필요하다.
새 OCI 컴퓨트 인스턴스를 띄울지, 사용자가 예전에 만들어둔 개인 k3s
클러스터를 쓸지부터 확인했다 — 근거 없이 새 인스턴스를 만들면 무료
티어를 낭비하거나(이미 다 썼을 수도 있음), 다른 목적으로 준비해둔
클러스터를 놔두고 중복 인프라를 만드는 셈이 된다.

## 확인 방법과 결과

- `oci limits resource-availability get`으로 실제 남은 할당량 확인 —
  Always Free `VM.Standard.A1.Flex`(Ampere ARM64) 4 OCPU/24GB 전체가
  기존 개인 k3s 3대(master 2 OCPU/12GB + worker 1 OCPU/6GB × 2)로
  이미 꽉 차 있음. `VM.Standard.E2.1.Micro` 무료 풀은 별도로 안 쓰고
  있었지만(0/2), 사용자가 기존 클러스터의 여유 용량을 쓰는 쪽을
  선택.
- 기존 클러스터 워크로드 확인(`kubectl get pods -A`) — `nginx-test`
  하나 + Prometheus/Grafana 모니터링 스택뿐, 새 워크로드를 얹을 여유
  충분.
- `oci network subnet get` / `security-list get`으로 k3s 노드
  VCN(`vcn-20260226`, 10.0.0.0/16)과 dividend-anatomy MySQL의 VCN이
  **같다**는 걸 확인. MySQL 보안 목록에 이미 "10.0.0.0/16 전체"에
  3306을 열어둔 규칙(`dividend-anatomy MySQL (VCN 내부만)`)이 있어서,
  클러스터에서 DB로 직접 접속 가능 — Bastion 터널이 전혀 필요 없다.
  배포 후 pod 로그에서 HikariCP가 `10.0.2.201:3306`에 바로 연결
  성공한 걸로 실제 확인.
- 로컬 개발 머신(Apple Silicon)과 클러스터 노드(Ampere ARM64)가 둘 다
  arm64라 크로스 컴파일 없이 네이티브 이미지 빌드 가능.
- 클러스터에 ingress controller/LoadBalancer가 없고, 기존
  `nginx-test`도 `NodePort`(30080, 0.0.0.0/0에 공개)로 노출 중임을
  확인 — 이 프로젝트만 별도로 TLS·ingress를 새로 도입하지 않고 같은
  패턴을 따르기로 함.

## 결정

- **새 인스턴스를 만들지 않고 기존 개인 k3s 클러스터에 새 워크로드로
  배포한다.**
- **컨테이너 레지스트리를 새로 만들지 않는다.** 로컬에서
  `docker build --platform linux/arm64` → `docker save` → 3개 노드에
  `scp` → 각 노드에서 `k3s ctr images import`로 사이드로드하고,
  Deployment에 `imagePullPolicy: Never`를 명시한다. 코드가 바뀌면
  이 과정을 수동으로 반복해야 하지만(자동 CI/CD 없음), 단발성
  포트폴리오 배포 범위에서는 레지스트리 인프라를 새로 도입하는 것보다
  낫다고 판단.
- **`NodePort`(30081, HTTP 평문)로 노출한다.** 기존 클러스터의 확립된
  패턴(`nginx-test`의 30080)과 동일 수준 — TLS는 의도적으로 범위 밖.
- **OCI 보안 목록은 추가만 한다.** 기존 규칙(SSH 22, ICMP, 기존
  NodePort 30080, k3s API 6443, MySQL 3306)을 그대로 두고 30081 규칙만
  덧붙인다.

## 배포 중 발견한 예상 밖 문제

보안 목록에 30081을 열었는데도 `master`와 두 워커 중 하나의 공인
IP로는 계속 응답이 없었다. 원인을 파고드니 **이 클러스터의 flannel
VXLAN 크로스노드 라우팅 자체가 깨져 있었다** — `master`에서 pod
IP(다른 노드에 뜬 pod)로 직접 curl해도 타임아웃, `ip -d link show
flannel.1`은 세그폴트로 죽음. 새로 생긴 버그가 아니라 **기존부터
있던 클러스터 상태**라는 걸, 이미 떠 있던 `nginx-test`도 자기가 뜬
노드(`master`)의 IP로만 응답하고 다른 노드 IP로는 똑같이 안 되는 걸
재현해서 확인했다.

클러스터 네트워킹 자체를 고치는 건 이번 배포 범위 밖이라고 판단해서,
`k8s/deployment.yaml`에 `nodeSelector: kubernetes.io/hostname:
worker-2`로 pod를 특정 노드에 고정하는 방식으로 우회했다. 이러면
pod가 재배포로 다른 노드로 옮겨가면서 공개 엔드포인트가 조용히
끊기는 일을 막을 수 있다 — 다만 그 노드(`worker-2`, 공인 IP
`134.185.97.175`)가 죽으면 이 우회 자체가 무력화된다는 한계는 그대로
남는다.

## 남은 위험

- **클러스터 크로스노드 네트워킹이 근본적으로 안 고쳐진 상태다.**
  `nodeSelector`는 증상 회피이지 원인 해결이 아니다 — 이 클러스터에
  다른 워크로드를 더 얹거나 진짜 운영에 쓰려면 flannel/VXLAN 설정을
  따로 점검해야 한다.
- 이미지 사이드로드 방식이라 코드가 바뀔 때마다 3개 노드에 수동으로
  다시 배포해야 한다 — 자동화된 CI/CD 파이프라인은 이 프로젝트
  범위에서 의도적으로 도입하지 않음.
- TLS 없음 — 데모/포트폴리오 목적의 알려진 제약이며, README와
  progress-log에도 명시했다.
