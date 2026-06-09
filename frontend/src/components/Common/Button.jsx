export function Button({ children, className = "", ...props }) {
  return (
    <button className={`button ${className}`.trim()} type="button" {...props}>
      {children}
    </button>
  );
}
