# Maple Health
Android pharmacokinetic tracking and sobriety timing app

## Download
[Download MapleHealth APK](releases/MapleHealth.apk)

### Medication Management
Input medication details including dosage, half-life, and absorption time.

### Dose Logging
Quick button/action to log when a dose is taken.

### Concentration Tracking
- Real-time calculation and visual graph tracking estimated medication levels/concentration in system over time based on half-life and absorption time.

### Notification Types
- Scheduled (e.g., daily at 8:00 PM).
- Low concentration (notifies when estimated concentration drops below a set threshold).
- Average maintaining (calculates dosing interval in hours needed to maintain target average concentration X, notifys accordingly).

### Count-up timers
- tracks time since a designated, resetable "reset point", particularly built for addictions and self harming behaviours.

### No ads
- I dont know how to program ads so there arent any

### No widgets
- god knows i tried

### deets
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3
- **Navigation**: Jetpack Navigation 3 (state-driven)
- **Adaptive Layouts**: Compose Material Adaptive library
- **Concurrency & Reactive Flows**: Kotlin Coroutines & Flow
- **Architecture**: MVVM with Jetpack ViewModel
- **Background Task Scheduling**: WorkManager / AlarmManager (for notification triggers)
