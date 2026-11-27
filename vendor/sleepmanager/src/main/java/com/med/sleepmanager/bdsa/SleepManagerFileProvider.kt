// SPDX-License-Identifier: GPL-3.0-or-later
// Distinct FileProvider class so SleepManager's provider doesn't merge with other modules' in the BDSA fork.
package com.med.sleepmanager.bdsa

import androidx.core.content.FileProvider

class SleepManagerFileProvider : FileProvider()
