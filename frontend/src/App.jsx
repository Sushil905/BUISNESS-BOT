import "./assets/styles.css";
import { AppRoutes } from "./routes/AppRoutes.jsx";
import { ChatProvider } from "./context/ChatContext.jsx";

export default function App() {
  return (
    <ChatProvider>
      <AppRoutes />
    </ChatProvider>
  );
}
