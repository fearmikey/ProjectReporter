# Add project specific R8 rules here.
# AGP will combine all keep rule files in src/main/keepRules to pass to R8
#
# For more details, see
#   https://d.android.com/r/tools/r8/keep-rules

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# ---------------------------------------------------------------------------
# Apache POI (Word/.docx export) + its OOXML/XmlBeans dependencies.
# POI resolves many of its schema implementation classes reflectively at
# runtime (Class.forName on generated "*Impl" classes keyed by QName), which
# R8's static analysis cannot see. Keep these packages intact rather than
# risk a runtime crash during export.
# ---------------------------------------------------------------------------
-keep class org.apache.poi.** { *; }
-dontwarn org.apache.poi.**
-keep class org.apache.xmlbeans.** { *; }
-dontwarn org.apache.xmlbeans.**
-keep class schemaorg_apache_xmlbeans.** { *; }
-dontwarn schemaorg_apache_xmlbeans.**
-keep class org.openxmlformats.** { *; }
-dontwarn org.openxmlformats.**
-keep class com.microsoft.schemas.** { *; }
-dontwarn com.microsoft.schemas.**
-keep class org.etsi.uri.** { *; }
-dontwarn org.etsi.uri.**
-keep class org.apache.commons.compress.** { *; }
-dontwarn org.apache.commons.compress.**
-dontwarn javax.xml.stream.**
-dontwarn org.w3c.dom.**
-dontwarn org.apache.batik.**
-dontwarn com.graphbuilder.**
-dontwarn org.osgi.**

# POI pulls in log4j-api for logging, which optionally references annotations from
# these libraries (bnd, findbugs/SpotBugs) that are not on the runtime classpath.
# They're compile-time-only/optional; safe to ignore.
-dontwarn aQute.bnd.**
-dontwarn edu.umd.cs.findbugs.**
-dontwarn org.apache.logging.log4j.**

# ---------------------------------------------------------------------------
# Room entities are read reflectively by the generated Room implementation;
# keep field names/annotations intact just in case a consumer rule is missed.
# ---------------------------------------------------------------------------
-keep class com.fearmikey.projectreporter.data.entity.** { *; }
