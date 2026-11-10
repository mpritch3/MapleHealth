An Android medication tracking app with pharmacokinetic tracking and custom notification rules:
- Medication Management: Input medication details including dosage, half-life, and absorption time.
- Dose Logging: Quick button/action to log when a dose is taken.
- System Concentration Tracking: Real-time calculation and visual graph tracking estimated medication levels/concentration in system over time based on half-life and absorption time.
- Flexible Notification Modes:
  a) Time-based schedule (e.g., daily at 8:00 PM).
  b) Low concentration-based (notifies when estimated concentration drops below threshold X).
  c) Average concentration-based (calculates dosing interval in hours needed to maintain target average concentration X, notifying accordingly).
- Count-up timers for tracking relapses, particularly built for addictions and self harming behaviours.

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3 styling
- **Navigation**: Jetpack Navigation 3 (state-driven)
- **Adaptive Layouts**: Compose Material Adaptive library
- **Concurrency & Reactive Flows**: Kotlin Coroutines & Flow
- **Architecture**: MVVM with Jetpack ViewModel
- **Background Task Scheduling**: WorkManager / AlarmManager (for notification triggers)