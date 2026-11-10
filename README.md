# Maple Health
Android pharmacokinetic tracking and sobriety tracking app. 
- lets you figure out your meds and track your mental health without being marketed to (eat shit I Am Sober)

## Download
[MapleHealth APK](releases/MapleHealth.apk)

## What's it do?
### Medication Management
Input medication details including dosage, half-life, and absorption time.

### Dose Logging
Quick button/action to log when a dose is taken.

### Groups
Easily put meds into groups so you dont have to hit four million buttons at bedtime

### Concentration Tracking
Real-time calculation and visual graph tracking estimated medication levels/concentration in system over time based on half-life and absorption time.

### Notification Types
- Scheduled (e.g., daily at 8:00 PM).
- Low concentration (notifies when estimated concentration drops below a set threshold).
- Average maintaining (calculates dosing interval in hours needed to maintain target average concentration X, notifys accordingly).

### Count-up timers
Tracks time since a designated, resetable "reset point", particularly built for addictions and self harming behaviours.

### No ads
I dont know how to program ads so there arent any

### Exportable, interpretable data
The time and current concentration of your meds are recorded in a csv whenever you take meds or you reset a timer.
- This is really good for looking back and seeing "yeah i had like no antidepressants in my system when i cut"
- These csvs are exportable and importable

## FAQ
### What's t½?
The half life of the meds you're taking; the amount of time, in hours that it takes for your body to remove half the meds from itself. After 5 half lives its pretty much gone

### What's tmax?
The amount of time it takes for your body to completely absorb the meds your taking into your bloodstream. In layman's its the amount of time it takes to peak.

### Will you make the widget functional?
no

### Will you publish this on the play store?
no

### How do I find t½ and tmax?
google dot com

## deets
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3
- **Navigation**: Jetpack Navigation 3 (state-driven)
- **Adaptive Layouts**: Compose Material Adaptive library
- **Concurrency & Reactive Flows**: Kotlin Coroutines & Flow
- **Architecture**: MVVM with Jetpack ViewModel
- **Background Task Scheduling**: WorkManager / AlarmManager (for notification triggers)
