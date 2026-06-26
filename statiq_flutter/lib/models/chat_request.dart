import 'chat_message.dart';

class ChatRequest {
  final String question;
  final String mode; // 'user' or 'analyst'
  final List<ChatMessage> messages;

  ChatRequest({
    required this.question,
    required this.mode,
    required this.messages,
  });

  Map<String, dynamic> toJson() => {
        'question': question,
        'mode': mode,
        'messages': messages.map((m) => m.toJson()).toList(),
      };
}
