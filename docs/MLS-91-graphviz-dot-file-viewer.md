# MLS-91 Graphviz Dot File Viewer

This file contains the project documentation for the MarsTech Graphviz Dot File Viewer. Please refer to this file for requirements, design, and usage instructions.

# Graphviz DOT File Viewer - New Project Idea

## Current State
New project concept for a cross-platform Graphviz DOT file viewer application.

## Requirements

### Core Functionality
- **CLI Interface**: Launch with `myApp myFile.dot` command
- **File Watching**: Automatic detection of file changes and display refresh
- **Cross-Platform**: Support for Linux, macOS, and potentially Windows

### Technical Stack Decision
- **Language**: Kotlin/JVM
- **Native Build**: GraalVM for native executable compilation
- **Rendering**: viz.js (https://github.com/mdaines/viz-js) for DOT visualization
- **UI**: Compose Desktop with JCEF WebView (avoiding JavaFX)
- **WebView**: JCEF (Java Chromium Embedded Framework)
- **CLI**: Clikt for command-line argument parsing

### Key Features
- Real-time file monitoring and auto-refresh
- DOT file parsing and visualization
- Cross-platform compatibility
- Command-line interface for easy integration

## Proposed Solution

### Architecture
1. **File Watcher**: Monitor DOT file using `java.nio.file.WatchService`
2. **Web Server**: Lightweight embedded server (Ktor or similar)
3. **Renderer**: HTML page with viz.js for DOT rendering
4. **Native Build**: GraalVM native-image for standalone executables

### Technology Benefits
- **GraalVM**: Fast startup, small memory footprint, native executables
- **viz.js**: Mature Graphviz port, excellent DOT compatibility
- **Kotlin**: Modern language with great tooling
- **Compose Desktop**: Modern UI toolkit, no JavaFX dependency
- **JCEF**: Full Chromium engine, cross-platform WebView support
- **Clikt**: Kotlin-native CLI library, excellent GraalVM compatibility

## Acceptance Criteria
- [ ] CLI application accepts DOT file path as argument
- [ ] Application displays graphical representation using viz.js
- [ ] File changes trigger automatic display refresh
- [ ] Support full Graphviz DOT syntax via viz.js

## Implementation Notes
- Use Ktor for embedded web server
- HTML template with viz.js integration
- JCEF WebView component with Compose Desktop
- File watching with coroutines
