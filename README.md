# VIPORecorder

אפליקציית Android להקלטת מסך בצורה חכמה (Segments לפי פעילות/איידל), שמירה באחסון פנימי מוסתר, וניגון רציף של כל הסגמנטים.

## דרישות
- Android Studio (גרסה חדשה)
- JDK 17
- Android 8+ (minSdk 26)

## הרצה (Debug)
1. לפתוח את התיקייה בפרויקט ב-Android Studio
2. Gradle Sync
3. Run על מכשיר

## שימוש
- `Start` מתחיל סשן הקלטה
- `Stop` מסיים סשן
- `Play latest` מנגן את הסשן האחרון
- `ספרייה` מציגה הקלטות עם סינון לפי זמן/אפליקציה

## הרשאות
- MediaProjection נדרש לאישור הקלטת מסך (חלון מערכת)
- Foreground Service מציג התראה קבועה בזמן הקלטה
- Accessibility (אופציונלי) לזיהוי פעילות/איידל + סינון לפי אפליקציה
- Overlay (אופציונלי) להצגת בועה צפה עם STOP בהחזקה 5 שניות

## מיקום שמירה (מוסתר)
`/data/data/com.vipo.recorder/files/records/sessions/<sessionId>/seg_XXXX.mp4`

## הפצה למשתמשים דרך GitHub
נעלה APK חתום ל-**GitHub Releases** בשם קבוע `VIPORecorder.apk`.

קישור הורדה לגרסה האחרונה:
`https://github.com/vipogroup/vipo-recorder/releases/latest/download/VIPORecorder.apk`

> חשוב: קובץ חתימה (`.jks`) הוא סודי ולא מעלים אותו ל-GitHub.
