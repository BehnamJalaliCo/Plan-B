plugins {
    alias(libs.plugins.planb.android.feature)
}

android {
    namespace = "com.behnamjalali.planb.feature.notebooks"
}

dependencies {
    // Plan-B Pro rich notes. Document scanning and Latin text recognition run in Google Play
    // services (no model in the APK; unavailable without Play services). Handwriting models are
    // downloaded on first use, only after the user agrees. Persian OCR is Tesseract with the
    // bundled "fas" model (tessdata_fast, ~0.4 MB in assets).
    implementation(libs.mlkit.document.scanner)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.digital.ink)
    implementation(libs.tesseract4android)
    implementation(libs.kotlinx.coroutines.play.services)
}
