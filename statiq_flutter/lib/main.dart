import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'providers/chat_provider.dart';
import 'screens/chat_screen.dart';

void main() {
  runApp(
    ChangeNotifierProvider(
      create: (_) => ChatProvider()..startNewSession(),
      child: const StatIQApp(),
    ),
  );
}

class StatIQApp extends StatelessWidget {
  const StatIQApp({super.key});

  @override
  Widget build(BuildContext context) {
    final provider = Provider.of<ChatProvider>(context);

    // Dynamic accent color based on selected mode
    final accentColor = provider.currentMode == 'user'
        ? const Color(0xFF0284C7) // Soft premium blue
        : const Color(0xFF6366F1); // Elegant indigo

    return MaterialApp(
      title: 'StatIQ - База знаний БНС',
      debugShowCheckedModeBanner: false,
      themeMode: provider.themeMode,
      theme: ThemeData(
        brightness: Brightness.light,
        primaryColor: accentColor,
        colorScheme: ColorScheme.light(
          primary: accentColor,
          secondary: accentColor.withValues(alpha: 0.8),
          surface: Colors.white,
          error: const Color(0xFFEF4444),
        ),
        scaffoldBackgroundColor: const Color(0xFFF8FAFC),
        cardColor: Colors.white,
        dividerColor: const Color(0xFFE2E8F0),
        useMaterial3: true,
      ),
      darkTheme: ThemeData(
        brightness: Brightness.dark,
        primaryColor: const Color(0xFFE3E3E3),
        colorScheme: const ColorScheme.dark(
          primary: Color(0xFFE3E3E3),
          secondary: Color(0xFF8E8E93),
          surface: Color(0xFF1C1B1F),
          onSurface: Color(0xFFE3E3E3),
          error: Color(0xFFEF4444),
        ),
        scaffoldBackgroundColor: const Color(0xFF131314),
        cardColor: const Color(0xFF232428),
        dividerColor: Colors.white10,
        useMaterial3: true,
      ),
      home: const ChatScreen(),
    );
  }
}
