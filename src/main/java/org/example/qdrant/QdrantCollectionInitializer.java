package org.example.qdrant.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.CreateCollection;
import io.qdrant.client.grpc.Collections.Datatype;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.HnswConfigDiff;
import io.qdrant.client.grpc.Collections.OptimizersConfigDiff;
import io.qdrant.client.grpc.Collections.PayloadSchemaType;
import io.qdrant.client.grpc.Collections.QuantizationConfig;
import io.qdrant.client.grpc.Collections.QuantizationType;
import io.qdrant.client.grpc.Collections.ScalarQuantization;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Collections.VectorsConfig;
import io.qdrant.client.grpc.Collections.WalConfigDiff;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QdrantCollectionInitializer implements ApplicationRunner {

    private final QdrantClient qdrantClient;

    @Value("${spring.ai.vectorstore.qdrant.collection-name}")
    private String collectionName;

    // 임베딩 모델 출력 차원. 모델과 다르면 저장 시 에러 발생
    @Value("${app.qdrant.vector-size:1024}")
    private int vectorSize;

    // 샤드 수. 데이터를 노드에 나눠 저장 (생성 후 변경 불가, 리샤딩 필요)
    //  - 단일 노드: 1
    //  - 클러스터: 보통 노드 수의 1~2배
    @Value("${app.qdrant.shard-number:1}")
    private int shardNumber;

    // 복제본 수. 노드 장애 대비 (단일 노드에서 2 이상이면 생성 실패)
    //  - 운영 클러스터 권장: 2~3
    @Value("${app.qdrant.replication-factor:1}")
    private int replicationFactor;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (qdrantClient.collectionExistsAsync(collectionName).get()) {
            log.info("Qdrant collection '{}' already exists. Skip creation.", collectionName);
            return;
        }

        qdrantClient.createCollectionAsync(
                CreateCollection.newBuilder()
                        .setCollectionName(collectionName)

                        // ===================== 벡터 설정 =====================
                        .setVectorsConfig(VectorsConfig.newBuilder()
                                .setParams(VectorParams.newBuilder()
                                        .setSize(vectorSize)

                                        // Cosine / Dot / Euclid / Manhattan (생성 후 변경 불가)
                                        .setDistance(Distance.Cosine)

                                        // 원본 벡터를 디스크(mmap)에 저장. RAM 절약, SSD/NVMe 권장
                                        .setOnDisk(true)

                                        // [추가] 벡터 저장 정밀도
                                        //  - Float32: 기본값, 가장 정확
                                        //  - Float16: 메모리/디스크 절반, 정확도 손실은 대부분 미미
                                        //  - Uint8  : 1/4로 줄지만 임베딩이 uint8 범위일 때만 사용
                                        .setDatatype(Datatype.Float16)
                                        .build())
                                .build())

                        // ===================== HNSW 인덱스 =====================
                        .setHnswConfig(HnswConfigDiff.newBuilder()
                                .setM(16)                // 연결 수. 클수록 정확도↑ 메모리↑
                                .setEfConstruct(100)     // 구축 시 탐색 범위. 클수록 품질↑ 구축 느림
                                .setOnDisk(false)        // 인덱스는 메모리 유지 (검색 속도)

                                // [추가] 세그먼트 크기(KB)가 이 값보다 작으면 HNSW 대신 전체 스캔
                                //  - 필터가 매우 엄격할 때도 이 기준으로 전체 스캔을 선택함
                                //  - 기본값 10000. 크게 하면 정확도 안정, 작게 하면 인덱스 사용 증가
                                .setFullScanThreshold(10000)
                                .build())

                        // ===================== Scalar Quantization =====================
                        .setQuantizationConfig(QuantizationConfig.newBuilder()
                                .setScalar(ScalarQuantization.newBuilder()
                                        .setType(QuantizationType.Int8)
                                        .setQuantile(0.99f)
                                        .setAlwaysRam(true)
                                        .build())
                                .build())

                        // ===================== payload =====================
                        .setOnDiskPayload(true)

                        // ===================== [추가] Optimizer 설정 =====================
                        // 백그라운드에서 세그먼트를 병합하고 인덱스를 만드는 정책
                        .setOptimizersConfig(OptimizersConfigDiff.newBuilder()

                                // 세그먼트가 이 크기(KB)를 넘으면 HNSW 인덱스를 생성. 기본값 20000
                                //  - 그 미만은 인덱스 없이 전체 스캔
                                //  - 대량 일괄 적재 시 크게 잡았다가 적재 후 낮추는 방식도 많이 씀
                                .setIndexingThreshold(20000)

                                // 세그먼트 개수. 0이면 CPU 코어 수에 맞춰 자동
                                //  - 많을수록: 동시 검색/쓰기 처리량↑
                                //  - 적을수록: 단일 검색 지연 시간↓ (세그먼트를 적게 훑음)
                                .setDefaultSegmentNumber(0)

                                // 삭제된 벡터 비율이 이 값을 넘으면 세그먼트를 정리(vacuum). 기본값 0.2
                                //  - 삭제/수정이 잦은 서비스에서 디스크 낭비를 막아줌
                                .setDeletedThreshold(0.2)
                                .build())

                        // ===================== [추가] WAL 설정 =====================
                        // 쓰기 전 로그. 장애 시 데이터 복구에 사용
                        .setWalConfig(WalConfigDiff.newBuilder()
                                // WAL 세그먼트 크기(MB). 기본값 32
                                //  - 쓰기가 매우 많다면 키우는 걸 고려
                                .setWalCapacityMb(32)
                                .build())

                        // ===================== [추가] 분산 설정 =====================
                        .setShardNumber(shardNumber)
                        .setReplicationFactor(replicationFactor)
                        .build()
        ).get();

        log.info("Qdrant collection '{}' created (size={}, Cosine, SQ int8, Float16).",
                collectionName, vectorSize);

        createPayloadIndexes();
    }

    // ===================== [추가] payload 인덱스 =====================
    // 필터 조건으로 쓰는 필드에는 반드시 만들어야 함.
    // 인덱스가 없으면 필터 검색 시 payload를 전부 읽어서 비교하므로 느려짐
    private void createPayloadIndexes() throws Exception {
        // Keyword: 정확히 일치 비교(eq, in)용 문자열 필드
        //  그 외 타입: Integer, Float, Bool, Datetime, Text(전문 검색), Geo
        qdrantClient.createPayloadIndexAsync(
                collectionName,
                "source",                     // 저장 시 넣은 metadata 키
                PayloadSchemaType.Keyword,
                null, null, null, null
        ).get();

        log.info("Payload index created on '{}'.", "source");
    }
}