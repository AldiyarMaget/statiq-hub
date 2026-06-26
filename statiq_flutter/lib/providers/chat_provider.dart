import 'package:flutter/material.dart';
import '../models/chat_message.dart';
import '../models/chat_request.dart';
import '../services/chat_api_service.dart';

class ChatSession {
  final String id;
  final String title;
  final List<ChatMessage> messages;

  ChatSession({
    required this.id,
    required this.title,
    required this.messages,
  });
}

class ChatProvider extends ChangeNotifier {
  final ChatApiService _apiService = ChatApiService();

  bool _isDarkMode = true;
  String _baseUrl = 'http://localhost:8090';
  String _currentMode = 'user'; // 'user' or 'analyst'
  bool _isLoading = false;

  final List<ChatSession> _sessions = [];
  String? _activeSessionId;

  // Getters
  bool get isDarkMode => _isDarkMode;
  String get baseUrl => _baseUrl;
  String get currentMode => _currentMode;
  bool get isLoading => _isLoading;
  List<ChatSession> get sessions => _sessions;
  String? get activeSessionId => _activeSessionId;

  ChatSession? get activeSession {
    if (_activeSessionId == null) return null;
    return _sessions.firstWhere((s) => s.id == _activeSessionId, orElse: () => _sessions.first);
  }

  List<ChatMessage> get activeMessages => activeSession?.messages ?? [];

  ThemeMode get themeMode => _isDarkMode ? ThemeMode.dark : ThemeMode.light;

  void toggleTheme() {
    _isDarkMode = !_isDarkMode;
    notifyListeners();
  }

  void updateBaseUrl(String url) {
    _baseUrl = url;
    notifyListeners();
  }

  void setMode(String mode) {
    _currentMode = mode;
    notifyListeners();
  }

  void startNewSession() {
    final newSession = ChatSession(
      id: DateTime.now().millisecondsSinceEpoch.toString(),
      title: 'Новый чат',
      messages: [],
    );
    _sessions.insert(0, newSession);
    _activeSessionId = newSession.id;
    notifyListeners();
  }

  void selectSession(String sessionId) {
    _activeSessionId = sessionId;
    notifyListeners();
  }

  void deleteSession(String sessionId) {
    _sessions.removeWhere((s) => s.id == sessionId);
    if (_activeSessionId == sessionId) {
      _activeSessionId = _sessions.isNotEmpty ? _sessions.first.id : null;
    }
    notifyListeners();
  }

  Future<void> sendMessage(String text) async {
    if (text.trim().isEmpty) return;

    if (_activeSessionId == null || _sessions.isEmpty) {
      startNewSession();
    }

    final session = activeSession!;
    
    // Add user message
    final userMessage = ChatMessage(
      role: 'user',
      content: text,
      timestamp: DateTime.now(),
    );
    session.messages.add(userMessage);

    // Rename session if it's the first message
    if (session.title == 'Новый чат') {
      final updatedTitle = text.length > 25 ? '${text.substring(0, 22)}...' : text;
      final updatedSession = ChatSession(
        id: session.id,
        title: updatedTitle,
        messages: session.messages,
      );
      final idx = _sessions.indexWhere((s) => s.id == session.id);
      if (idx != -1) {
        _sessions[idx] = updatedSession;
      }
    }

    _isLoading = true;
    notifyListeners();

    try {
      // Build history excluding the latest user message
      final history = session.messages.sublist(0, session.messages.length - 1);

      final request = ChatRequest(
        question: text,
        mode: _currentMode,
        messages: history,
      );

      final response = await _apiService.sendChatRequest(_baseUrl, request);

      final assistantMessage = ChatMessage(
        role: 'assistant',
        content: response.answer,
        timestamp: DateTime.now(),
      );
      session.messages.add(assistantMessage);
    } catch (e) {
      final errorMessage = ChatMessage(
        role: 'assistant',
        content: '⚠️ Ответ из общих знаний\n\n❌ **Ошибка подключения к серверу**:\n\n```\n$e\n```\n\nПожалуйста, убедитесь, что бэкенд запущен по адресу `$_baseUrl` и доступен.',
        timestamp: DateTime.now(),
      );
      session.messages.add(errorMessage);
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }
}
