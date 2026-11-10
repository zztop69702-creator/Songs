# 영일 음향비전 — R8 규칙
# 저장 JSON(측정·보정 프로파일·현장) 호환성 보호: 직렬화 모델의 이름·필드를 유지
-keep class kr.youngil.acousticvision.core.model.** { *; }
-keep class kr.youngil.acousticvision.core.calibration.** { *; }
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
# 오류 보고 시 줄번호 확인용
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
