package org.whitedoggy.mapleweb2.global.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * 계산 결과 캐시의 세대 표. 계산에 관여하는 코드와 표가 바뀌면 값도 바뀐다.
 *
 * <p>전투력 계산 결과(추이 지점·데이터시트·오늘 앞머리)는 30일까지 캐시된다. 계산 규칙을 고치고
 * 캐시 키의 버전을 안 올리면 고친 값이 한 달 동안 안 보인다 — 실제로 데몬어벤져 지원 배포에서
 * 그랬고(전투력 0 이 "계산 실패"로 굳음), 그 뒤로 접두사 버전을 손으로 올려 왔다.
 *
 * <p>손으로 올리는 것은 잊기 마련이라, 부팅 때 <b>계산에 관여하는 것들의 지문</b>을 재 둔다.
 * {@link CalculatedCache} 가 값마다 이 지문을 적고, 꺼낼 때 지금 지문과 다르면 다시 계산해
 * 덮어쓴다. 지문에 들어가는 것:
 * <ul>
 * <li>{@code analysis}·{@code domain} 패키지의 컴파일된 클래스 전부 (계산·파서·시트·DTO 모양)</li>
 * <li>계산이 읽는 표: {@code game-data.yml}, {@code event-buffs.yml}, {@code challengers-buffs.yml},
 *     {@code hexa-core-cost.yml}, {@code set/*.json}</li>
 * </ul>
 * 둘 중 하나라도 바뀌면 지문이 달라져 옛 값은 히트로 치지 않고 새 값으로 바뀐다.
 * 화면·컨트롤러·인프라만 고친 배포는 지문이 그대로라 캐시가 산다.
 *
 * <p>javac 은 같은 소스에서 같은 바이트를 내므로 재빌드해도 지문이 흔들리지 않는다. 혹시
 * 흔들리더라도 배포 한 번에 캐시가 한 번 비는 것이 최악이다 — 옛 값을 30일 보여 주는 것보다 낫다.
 */
@Slf4j
@Component
public class CalculationVersion {

    private static final List<String> PATTERNS = List.of(
            "classpath*:org/whitedoggy/mapleweb2/analysis/**/*.class",
            "classpath*:org/whitedoggy/mapleweb2/domain/**/*.class",
            "classpath*:game-data.yml",
            "classpath*:event-buffs.yml",
            "classpath*:challengers-buffs.yml",
            "classpath*:hexa-core-cost.yml",
            "classpath*:set/*.json");

    private final String tag;

    public CalculationVersion() {
        this(fingerprint());
    }

    private CalculationVersion(String tag) {
        this.tag = tag;
    }

    /** 테스트용. 지문을 재지 않고 주어진 값을 쓴다. */
    static CalculationVersion fixed(String tag) {
        return new CalculationVersion(tag);
    }

    /** 캐시 값에 적을 짧은 지문. 12자리 16진수. */
    public String tag() {
        return tag;
    }

    private static String fingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = Arrays.stream(collect(resolver))
                    // 경로 순으로 넣어야 클래스패스 순서와 무관하게 같은 값이 나온다.
                    .sorted(Comparator.comparing(CalculationVersion::pathOf))
                    .toArray(Resource[]::new);
            for (Resource resource : resources) {
                digest.update(pathOf(resource).getBytes());
                try (InputStream in = resource.getInputStream()) {
                    digest.update(in.readAllBytes());
                }
            }
            String tag = HexFormat.of().formatHex(digest.digest()).substring(0, 12);
            // 파일 수를 같이 적어 둔다 — 패턴이 아무것도 못 찾으면 지문이 늘 같아 버려서, 그걸 여기서 알아챈다.
            log.info("계산 세대: {} (클래스·표 {}개)", tag, resources.length);
            return tag;
        } catch (IOException | NoSuchAlgorithmException error) {
            throw new IllegalStateException("계산 세대를 잴 수 없습니다", error);
        }
    }

    private static Resource[] collect(PathMatchingResourcePatternResolver resolver) throws IOException {
        return PATTERNS.stream()
                .flatMap(pattern -> {
                    try {
                        return Arrays.stream(resolver.getResources(pattern));
                    } catch (IOException error) {
                        throw new IllegalStateException(pattern, error);
                    }
                })
                .filter(Resource::isReadable)
                .toArray(Resource[]::new);
    }

    /**
     * 정렬·지문용 경로. jar 안이든 디렉터리든 {@code org/whitedoggy/...} 아래만 남겨,
     * 빌드 디렉터리 이름이나 jar 위치가 지문에 섞이지 않게 한다.
     */
    private static String pathOf(Resource resource) {
        String path;
        try {
            path = resource.getURL().toString();
        } catch (IOException error) {
            path = resource.getDescription();
        }
        int at = path.indexOf("org/whitedoggy/");
        if (at >= 0) {
            return path.substring(at);
        }
        // 표 파일은 jar 에서 "!/BOOT-INF/classes/" 뒤, 개발 중엔 "…/resources/main/" 뒤가 클래스패스 경로다.
        // 둘을 같은 모양으로 맞춰야 로컬과 운영의 세대가 같은 값으로 찍혀 비교할 수 있다.
        int bang = path.lastIndexOf("!/");
        if (bang >= 0) {
            return path.substring(bang + 2).replaceFirst("^BOOT-INF/classes/", "");
        }
        int main = path.lastIndexOf("/main/");
        return main >= 0 ? path.substring(main + 6) : resource.getFilename();
    }
}
