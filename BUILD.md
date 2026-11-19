# איך בונים ומפרסמים גרסה

## בקצרה
GitHub בונה את האפליקציה לבד. כדי לפרסם גרסה חדשה:

1. ב-`version.properties` – להעלות את שני המספרים (למשל `VERSION_NAME=0.2`, `VERSION_CODE=2`).
   `VERSION_CODE` חייב תמיד לעלות, אחרת הטלפון לא יציע עדכון.
2. ב-`release-notes.txt` – לכתוב בעברית מה חדש (זה מה שיופיע באפליקציה ובדף ההורדה).
3. לשמור ב-Git ולדחוף ל-`main`.

תוך כמה דקות:
- נוצרת גרסה (Release) ב-GitHub עם הקובץ `haklatot.apk`.
- דף ההורדה ו-`version.json` מתעדכנים ב-https://tzipdvir-afk.github.io/haklatot/
- באפליקציה בטלפון מופיע "גרסה X מוכנה" (עד כ-10 דקות אחר כך, בגלל זיכרון מטמון של GitHub).

דחיפה ל-`main` בלי לשנות את מספר הגרסה – רק בונה ובודקת (הקובץ נשמר ב-Actions → Artifacts), בלי לפרסם.

## מפתח החתימה – חשוב מאוד
כל גרסה חתומה באותו מפתח. **בלי המפתח אי אפשר לעדכן את האפליקציה בטלפון** – רק למחוק ולהתקין מחדש.

| איפה | מה |
|---|---|
| `Documents\haklatot-signing\haklatot-release.jks` | קובץ המפתח (במחשב, מחוץ לפרויקט) |
| `Documents\haklatot-signing\קרא-אותי-סיסמת-חתימה.txt` | הסיסמה והכינוי |
| GitHub → Settings → Secrets → Actions | עותק מוצפן לשימוש הבנייה: `KEYSTORE_B64`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` |
| **גיבוי** | להעתיק את כל תיקיית `haklatot-signing` ל-Google Drive או לדיסק און קי |

המאגר ציבורי – **אסור** להכניס אליו את המפתח או את הסיסמה (`.gitignore` חוסם זאת).

## בנייה במחשב (לא חובה)
לבדיקה מהירה בלי GitHub:
```
$env:JAVA_HOME="C:\Users\USER\.jdks\jdk-21.0.12.1+1"
.\gradlew.bat assembleRelease
```
הקובץ נוצר ב-`app\build\outputs\apk\release\app-release.apk`, חתום באותו מפתח (דרך `keystore.properties` המקומי, שאינו נשמר ב-Git).

## התקנה בטלפון
פעם ראשונה: לפתוח בטלפון את https://tzipdvir-afk.github.io/haklatot/ וללחוץ "הורד והתקן" (ההוראות בדף).
אחר כך: עדכונים מגיעים מתוך האפליקציה.

## כלים
- Gradle 8.11.1, Android Gradle Plugin 8.7.2, Kotlin 2.0.21, Jetpack Compose (BOM 2024.12.01)
- compileSdk / targetSdk 35, minSdk 29
- ב-GitHub: Java 17 (Temurin). במחשב: JDK 21.
