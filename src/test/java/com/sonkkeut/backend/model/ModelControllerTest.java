package com.sonkkeut.backend.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class ModelControllerTest {
	@Test
	void 통합_모델의_버전_해시_다운로드_주소를_반환한다() {
		ModelInfo info = new ModelController("", "", JsonMapper.builder().build()).latest();
		assertThat(info.version()).isEqualTo("2026.10.03");
		assertThat(info.files()).hasSize(5);
		ModelInfo.ModelFile ocr = info.files().stream().filter(f -> f.name().equals("m3_kiosk_rec_v2.onnx")).findFirst().orElseThrow();
		assertThat(ocr.sha256()).isEqualTo("80d6462d5cc0d0a189ecd810940df484902594a11087a4592e8a8dec47d87145");
		assertThat(ocr.sizeBytes()).isEqualTo(13413875L);
		ModelInfo.ModelFile speech = info.files().stream().filter(f -> f.name().equals("whisper-elder-v3-ct2.zip")).findFirst().orElseThrow();
		assertThat(speech.url()).isEqualTo("https://github.com/fingertip-vision/sonkkeut-ai/releases/download/asr-whisper-elder-v3/whisper-elder-v3-ct2.zip");
		assertThat(speech.sha256()).isEqualTo("d6d5b3c3efbc7be6b9d6585fca8155e4418fa00f443eeb9aff464075da37c3ea");
		assertThat(speech.sizeBytes()).isEqualTo(484672869L);
		assertThat(info.files().getFirst().url()).isNull();
	}

	@Test
	void 운영자가_지정한_버전과_모델_주소는_유지한다() {
		ModelInfo info = new ModelController("operator-version", "https://models.example.test/", JsonMapper.builder().build()).latest();
		assertThat(info.version()).isEqualTo("operator-version");
		assertThat(info.files()).allSatisfy(f -> assertThat(f.url()).isEqualTo("https://models.example.test/" + f.name()));
	}
}
