# 11. CI/CD 파이프라인 도입 — GitHub Actions + GHCR

**날짜**: 2026-08-25

## 왜 다시 검토했나

`docs/decisions/08-deployment.md`는 "자동 CI/CD 없음"을 의도적 결정으로
남겼다 — 당시엔 단발성 포트폴리오 배포라 수동 4단계(`docker build` →
`docker save` → `scp` x3 → `k3s ctr images import` x3 →
`rollout restart`)로 충분하다고 판단했다.

이번에 N+1 쿼리 수정을 배포하려고 그 수동 절차를 다시 밟다가, 매번
코드가 바뀔 때마다 408MB 이미지를 노드 3대에 손으로 옮기는 게 실제로는
매번 몇 분씩 걸리고 실수하기 쉬운 작업이라는 걸 재확인했다. 배포
빈도가 "가끔"에서 "코드를 고칠 때마다"로 바뀐 시점에 수동 절차의
비용이 맞지 않게 됐다고 판단해 결정을 뒤집는다.

## 검토한 대안과 이유

- **ArgoCD(GitOps)**: 기각. 클러스터가 Always Free 할당량(4 OCPU/24GB)을
  k3s+모니터링+이 앱으로 이미 다 쓰고 있어서, ArgoCD 자체의 컨트롤러/
  레포서버/Redis 상주 자원을 새로 얹을 여유가 없다. 또 ArgoCD의 진짜
  가치(여러 앱·환경의 지속 동기화·드리프트 감지)는 앱 1개·환경 1개
  규모에서는 안 나온다.
- **이미지 저장소: ECR 대신 GHCR**: 이 프로젝트 인프라는 전부 OCI라
  ECR을 쓰면 AWS를 세 번째 클라우드로 새로 끌어들이는 셈이고, ECR
  인증 토큰은 12시간마다 만료돼 갱신 메커니즘이 따로 필요하다. GHCR은
  이미 쓰고 있는 GitHub 계정의 `GITHUB_TOKEN`으로 바로 인증되고, 이미지
  자체를 public으로 두면 k3s 노드가 인증 없이 그냥 pull만 하면 된다.
- **호스티드 러너: arm64 대신 QEMU 크로스빌드**: 저장소를 private으로
  결정했는데, GitHub의 무료 arm64 호스티드 러너는 public 저장소
  전용이다. private 저장소는 `ubuntu-latest`(amd64) + QEMU
  에뮬레이션으로 크로스빌드한다 — 빌드가 느려지지만(로컬 arm64
  네이티브 대비 몇 배) 배포 빈도상 문제 없고, private 저장소 기본
  제공 Actions 분당 한도 안에서 충분하다.
- **이미지 자체는 public**: 저장소는 private으로 남기되, GHCR 패키지는
  public으로 설정했다 — k3s 쪽 `imagePullSecret`/PAT 발급·갱신 부담을
  없애기 위해서다. 이미지를 디컴파일하면 소스와 비슷한 수준의 로직이
  드러날 수 있다는 트레이드오프가 있지만, DB 비밀번호 등은 여전히
  `k8s/secret.yaml`(gitignore, 클러스터 시크릿)에만 있고 이미지엔
  포함되지 않는다.

## 배포 키 분리

기존 개인 SSH 키(`~/.ssh/id_ed25519`, 모든 노드에 이미 등록됨)를
GitHub Actions 시크릿에 그대로 넣지 않고, CI 전용 키
(`dividend-anatomy-ci-deploy`)를 새로 만들어 master의
`authorized_keys`에 추가로 등록했다 — 나중에 CI 키만 따로 폐기할 수
있게 하기 위해서다.

## 새 배포 흐름

`main` 푸시 → GitHub Actions가 `ubuntu-latest`에서 QEMU로 arm64 이미지
빌드 → `ghcr.io/king0104/dividend-anatomy`(public)에
`:latest`·`:<sha>` 태그로 push → master에 SSH로 접속해
`kubectl set image` + `rollout status`로 롤아웃.

`docker save`/`scp`/`ctr images import` 단계가 전부 사라진다 — 어느
노드에 스케줄되든 그 노드가 GHCR에서 직접 pull한다(`imagePullPolicy:
Always`로 변경, 기존 `Never`는 사이드로드 전제였던 설정).

## 후속 — 빌드 캐시 (2026-08-25)

QEMU 크로스빌드가 매번 8~10분 걸려서, 소스만 바뀌어도 Gradle
배포판·의존성을 처음부터 다시 받는 게 낭비였다. Dockerfile을 두
단계로 나눠(의존성 관련 파일만 먼저 COPY → `gradlew dependencies`로
캐시 예열 → 나머지 소스 COPY → `bootJar`) 의존성 다운로드 레이어와
컴파일 레이어를 분리하고, `docker/build-push-action`에
`cache-from`/`cache-to: type=gha`를 추가해 그 레이어 캐시를 Actions
실행 간에 유지되게 했다. QEMU 에뮬레이션 자체의 오버헤드는 그대로라
근본적인 해결은 아니고(진짜 해결책은 네이티브 arm64 호스티드 러너,
아직 private 저장소 무료 한도 적용 여부 미확인이라 보류), 의존성이
안 바뀐 반복 빌드의 시간만 줄어든다.

이 저장소는 GitHub Actions 무료 포함 분수(월 2,000분, private 기준)
안에서 운영하는 게 원칙이다 — arm64 네이티브 러너 전환은 과금
여부가 문서만으로 100% 확인되지 않아 보류했다. Public 저장소였다면
호스티드 러너(arm64 포함)가 무제한 무료라 이 문제 자체가 없다.

## 남은 위험

- GitHub Actions 러너가 죽거나 GHCR/GitHub 자체 장애가 나면 배포가
  막힌다 — 그럴 땐 예전 수동 절차(`docs/infrastructure.md` 재배포
  절차, 이번에 GHCR pull로 갱신)로 되돌아갈 수 있다.
- CI 배포 키가 탈취되면 master에 SSH 접근이 가능하다 — 개인 키와
  분리해뒀으니 이 키만 `authorized_keys`에서 제거하면 즉시 무효화
  가능하다.
