// SPDX-License-Identifier: GPL-2.0-or-later
// Distinct FileProvider class so ThorTune's provider doesn't merge with other modules' in the BDSA fork.
package com.androosio.thortune.bdsa

import androidx.core.content.FileProvider

class ThorTuneFileProvider : FileProvider()
