import 'dart:convert';
import 'package:http/http.dart' as http;
import '../models/chat_request.dart';
import '../models/chat_response.dart';

class ChatApiService {
  Future<ChatResponse> sendChatRequest(String baseUrl, ChatRequest request) async {
    final url = Uri.parse('$baseUrl/api/chat');
    final response = await http.post(
      url,
      headers: {
        'Content-Type': 'application/json; charset=utf-8',
        'Accept': 'application/json',
      },
      body: jsonEncode(request.toJson()),
    ).timeout(const Duration(seconds: 90));

    if (response.statusCode == 200) {
      final decoded = jsonDecode(utf8.decode(response.bodyBytes));
      return ChatResponse.fromJson(decoded);
    } else {
      throw Exception('Ошибка сервера (${response.statusCode}): ${response.body}');
    }
  }
}
