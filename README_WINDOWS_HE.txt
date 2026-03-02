VIPORecorder (Android) - הקלטת מסך חכמה (Segments) + אחסון מוסתר + חיפוש

מה זה עושה?
- מקליט מסך בוידאו MP4
- שומר את הקבצים בתוך Internal App Storage (מוסתר: לא בגלריה/Files)
- עוצר אוטומטית כשאין שימוש במכשיר (Idle) ומתחיל שוב כשיש פעילות
- שומר את ההקלטה כסשן אחד שמורכב מכמה קטעים (Segments)
- ניגון רציף של כל הסגמנטים כאילו זה וידאו אחד (Playlist) בתוך האפליקציה

דרישות:
- Android Studio (Hedgehog / Iguana / חדש)
- JDK 17 (Android Studio כבר מגיע עם Embedded JDK)
- מכשיר/אמולטור: Android 8+ (minSdk 26)

איך להריץ:
1) פתח את התיקייה VIPORecorder ב-Android Studio
2) Sync Gradle
3) Run (Debug) על מכשיר
4) בפעם הראשונה:
   - תתבקש להפעיל "נגישות" לשירות VIPO Recorder Idle (כדי לזהות פעילות/חוסר פעילות)
   - בלחיצה על Start תתבקש לאשר הקלטת מסך (MediaProjection)

כפתורים:
- Start: מתחיל סשן הקלטה (Segments)
- Stop: מסיים סשן
- Play latest: מנגן את הסשן האחרון (כל הסגמנטים ברצף)

הערות חשובות:
- Android מחייב חלון אישור כשמתחילים הקלטת מסך
- בזמן הקלטה מופיעה התראה קבועה (Foreground Service)
- סאונד פנימי של אפליקציות לא תמיד יוקלט; כרגע הקוד מקליט וידאו בלבד (אפשר להוסיף MIC בקלות בהמשך)

קבצים נשמרים כאן (מוסתר):
/data/data/com.vipo.recorder/files/records/sessions/<sessionId>/seg_XXXX.mp4

בהמשך אפשר להוסיף:
- עצירה רק בלחיצה ארוכה 5 שניות בבועה צפה
- הצפנה AES-GCM לכל קובץ (enc) + פתיחה בביומטרי
- סיווג אוטומטי לשיחות/WhatsApp (דורש מודולים נוספים)


Overlay (בועה צפה):
- בזמן הקלטה, אם מאשרים 'Display over other apps', תופיע בועה/פאנל קטן עם STOP.
- STOP עובד רק בהחזקה 5 שניות.
- הרשאה: Settings > Apps > Special access > Display over other apps > VIPORecorder.
