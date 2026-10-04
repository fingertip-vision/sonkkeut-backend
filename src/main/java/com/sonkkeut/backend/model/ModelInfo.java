package com.sonkkeut.backend.model;

import java.util.List;

/** 앱이 가진 모델과 서버가 아는 최신 모델을 비교하는 정보. url이 null이면 앱은 APK에 든 모델을 쓴다. */
public record ModelInfo(String version, List<ModelFile> files) {

	public record ModelFile(String name, String url, String sha256, Long sizeBytes) {
	}
}
