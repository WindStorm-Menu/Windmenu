// 1. Configuração de conexão com o seu banco de dados
// Subastitua os valores abaixo com as informações do seu painel do Supabase
const SUPABASE_URL = "https://wdjfwtckllamgjmqiurf.supabase.co";
const SUPABASE_ANON_KEY = "sb_publishable_5_lemuRE8Q4I0At4JsNxJQ__8b88vbs";

// Inicializa a biblioteca do Supabase
const supabase = supabase.createClient(SUPABASE_URL, SUPABASE_ANON_KEY);

// 2. Função para carregar e mostrar os códigos na tela de quem acessar
async function renderScriptFeed() {
    const container = document.getElementById('script-feed-container');
    if (!container) return;

    // Busca as informações direto da tabela na nuvem
    let { data: codes, error } = await supabase
        .from('uploaded_codes')
        .select('*')
        .order('id', { ascending: false });

    if (error) {
        console.error("Erro ao puxar dados do banco:", error.message);
        return;
    }

    container.innerHTML = '';

    if (!codes || codes.length === 0) {
        container.innerHTML = `<p style="font-size:13px; color:var(--text-muted);">Nenhum código disponível no momento.</p>`;
        return;
    }

    // Monta o visual de cada código para o usuário
    codes.forEach((c) => {
        let card = document.createElement('div');
        card.className = 'script-card';
        card.innerHTML = `
            <h4><span>${c.title} <span class="role-badge role-${c.role}" style="font-size:9px;">${c.role}</span></span></h4>
            <p>${c.desc || 'Sem descrição.'}</p>
            <div class="output-container">
                <textarea id="feed-code-${c.id}" readonly style="width:100%; height:65px; font-family:monospace; padding:8px; font-size:11px; background:rgba(0,0,0,0.3); border:1px solid var(--glass-border); color:#fff; border-radius:8px; resize:none;">${c.code}</textarea>
                <button class="copy-btn" onclick="copyToClipboard('feed-code-${c.id}')">Copy</button>
            </div>
        `;
        container.appendChild(card);
    });
}


function ativarSincronizacaoGlobal() {
    supabase
        .channel('canal-de-transmissao')
        .on(
            'postgres_changes', 
            { 
                event: '*', 
                schema: 'public', 
                table: 'uploaded_codes' 
            }, 
            (payload) => {
                console.log("Nova informação recebida do banco!", payload);
                renderScriptFeed(); // Atualiza a lista na tela imediatamente
            }
        )
        .subscribe();
}

// Inicializa as funções assim que a página abre
window.addEventListener('DOMContentLoaded', () => {
    renderScriptFeed();
    ativarSincronizacaoGlobal();
});
