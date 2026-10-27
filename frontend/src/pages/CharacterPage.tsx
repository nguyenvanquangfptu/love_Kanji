import { Navigate, useNavigate, useParams } from 'react-router-dom'
import { ChevronLeft } from 'lucide-react'
import { PageHeader } from '@/components/PageHeader'
import { CharacterDetailView } from '@/components/CharacterDetailView'

/** Trang riêng của một chữ Hán: các từ trong kho có chữ đó, gom theo âm Hán Việt. */
export function CharacterPage() {
  const { character } = useParams()
  const navigate = useNavigate()
  if (!character) return <Navigate to="/study" replace />
  return (
    <div className="mx-auto max-w-2xl">
      <button
        type="button"
        onClick={() => navigate(-1)}
        className="mb-4 inline-flex items-center gap-1 rounded-xl py-1 pr-2 text-sm font-extrabold text-muted-foreground hover:text-foreground"
      >
        <ChevronLeft className="h-5 w-5" />
        Quay lại
      </button>
      <PageHeader title={`Chữ ${character}`} subtitle="Các từ trong kho có chữ này, theo từng âm Hán Việt." />
      <CharacterDetailView character={character} />
    </div>
  )
}
