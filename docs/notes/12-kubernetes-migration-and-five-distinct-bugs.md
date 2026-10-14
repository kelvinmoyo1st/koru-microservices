# 12 - Migrating to Kubernetes: five genuinely distinct bugs, one proven end-to-end result

## Why Kubernetes is a different model, not just different syntax
`docker-compose` starts containers once. Kubernetes continuously *enforces* a desired state - a dead
container gets replaced automatically - and splits "how to run this" (`Deployment`) from "how to reach
this" (`Service`) into two separate resources, rather than one block per service.

## The five distinct failures, in the order they were found

### 1. Missing controller port on the Kafka Service
Single-node KRaft Kafka needs to reach its own controller listener (port 9093) through the Service,
since `KAFKA_CONTROLLER_QUORUM_VOTERS` pointed at the Service DNS name, same as every other address.
The Service only declared port 9092 - nothing routed 9093 at all. Fix: add the port, and once a Service
has more than one port, Kubernetes requires every port to be explicitly named (`client`/`controller`).

### 2. Hairpin NAT - a pod unable to reach itself through its own Service
Even with the port fixed, registration still timed out. A single-node broker's controller and broker
are the same process in the same pod - but the Service DNS name routes through the cluster's virtual IP
and back, a traffic pattern (a pod calling its own Service) that requires explicit "hairpin NAT" support
from the CNI plugin, which `kindnet` (minikube's default) doesn't reliably provide. Fix (confirmed via a
well-reasoned second-opinion suggestion, verified before trusting it): point
`KAFKA_CONTROLLER_QUORUM_VOTERS` at `localhost:9093` instead - broker and controller are the same
process, so this never needed to leave the pod in the first place. `KAFKA_ADVERTISED_LISTENERS` stayed
as the Service name, since *other* pods genuinely do need to reach in from outside.

### 3. Stale Spring profile baked into already-loaded images
`order-service`/`notification-service` pods ran fine but notification never arrived. Root cause: the
`k8s` Spring profile (`application-k8s.properties`, pointing Kafka at `kafka:9092`) was created *after*
the Docker images already in minikube were built - profile files are baked in at build time, not
discoverable later. The running containers silently fell back to the base config's `localhost:9092`,
which inside a container resolves to nothing. Fix: rebuild both images, reload into minikube,
`kubectl rollout restart` to force the Deployments onto the new image - simply restarting the pods
without rebuilding would have changed nothing, since the image itself was still stale.

### 4. Readiness probe timing out under genuine resource contention
Kafka crash-looped with `command timed out ... after 5s` on its own healthcheck. Node-level evidence
(`kubectl describe node`) ruled out true memory pressure (`MemoryPressure: False`) and ruled out a true
OOM-kill (exit code 143, SIGTERM, not 137). The real cause: running the full Docker Compose stack
*and* a live minikube cluster simultaneously is genuinely tight on this machine, and a 5-second ceiling
for a JVM CLI command under real CPU contention is simply too strict. Fix: `timeoutSeconds: 5 -> 15`,
`failureThreshold: 5 -> 10` - an honest, explainable operational tuning for a resource-constrained
environment, not a mysterious flake.

### 5. The Codespaces iptables bug resurfacing after `minikube delete`
Docker's own build cache became corrupted after a `minikube delete`/recreate cycle (RWLayer
corruption), requiring `minikube delete` and a full rebuild. That rebuild reset minikube's container
networking state, and the exact `registry.k8s.io` connectivity block from the very first Kubernetes
session reappeared - same root cause as documented in note 11, same fix
(`sudo iptables-legacy -I DOCKER-USER -j ACCEPT`), confirmed this time with `curl -v` for an
unambiguous, non-silent result rather than trusting an empty terminal line.

## The actual proof
Product created, order placed at `08:52:57.952` through `order-service`'s pod via `kubectl
port-forward` (the correct way to reach a cluster-internal Service from outside without permanently
exposing it). `notification-service` - a fully separate pod, reached only through the `kafka` Service -
logged `Notifying customer: order 1 placed...` at `08:53:02.405`, under 5 seconds later. Three
independent Kubernetes-managed pods, genuinely orchestrated, genuinely communicating.

## A process lesson, not just a technical one
Several commands this session were pasted as stale output from much earlier in the conversation,
mistaken for current state. Fixed by adopting one habit consistently: check the actual wall-clock time
at the start of any session or after a gap, and never trust a pasted result without confirming it's from
a command that was actually just run to completion. This caught real problems (minikube container
corruption, images never actually loaded) that would otherwise have been invisible.

## Interview one-liners
- "I debugged five genuinely distinct Kubernetes/Docker failures in one migration - a missing Service
  port, hairpin NAT, a stale baked-in config, resource-constrained probe tuning, and a host networking
  bug - each confirmed with direct evidence, not assumed fixed."
- "I learned the hard way that a Spring profile only takes effect if it existed before the image was
  built - restarting a pod never re-reads a jar that's already been packaged."
- "When a 'fix' comes from a second AI opinion, I verify its reasoning against the actual evidence
  before applying it, and I check whether it's still the right prescription for where I currently am,
  not just whether the reasoning sounds plausible."

## Known gaps to revisit
- Running Docker Compose and minikube simultaneously on this machine is tight enough to need active
  management (stopping Compose temporarily) during heavy operations like image builds - worth
  documenting plainly rather than treating as solved.
- The non-persistent iptables fix now needs reapplying after *either* a Codespace restart or a
  `minikube delete` - worth folding into `start-stack.sh` or a parallel `start-k8s.sh` helper.
