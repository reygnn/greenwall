package com.github.reygnn.greenwall

import android.app.Application

/**
 * Application-wide composition root. Kept minimal; add long-lived
 * collaborators (e.g. a PreferencesRepository) here if/when needed.
 */
class GreenwallApplication : Application()
