package com.kanjimastery.backend;

import org.junit.jupiter.api.Test;

class KanjiMasteryApplicationIT extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
        // Chỉ cần Spring context khởi động thành công trên Postgres/Redis thật (Testcontainers)
        // là đủ để phát hiện sớm lỗi cấu hình bean/migration trước khi deploy.
    }
}
